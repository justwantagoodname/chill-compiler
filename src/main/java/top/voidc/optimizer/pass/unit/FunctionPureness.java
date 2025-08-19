package top.voidc.optimizer.pass.unit;

import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.IceCallInstruction;
import top.voidc.ir.ice.instruction.IceGEPInstruction;
import top.voidc.ir.ice.instruction.IceLoadInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.annotation.Qualifier;
import top.voidc.misc.ds.ChilletGraph;
import top.voidc.optimizer.pass.CompilePass;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Pass(group = {"O0", "analysis"})
public class FunctionPureness implements CompilePass<IceUnit> {

    public enum Pureness {
        CONST, // 无副作用且仅依赖参数
        PURE, // 无副作用，但可以依赖全局状态（只读）
        IMPURE, // 有副作用，可能会修改全局状态或参数
    }

    public static class PurenessInfo {
        private Pureness pureness;
        public Set<IceGlobalVariable> readGlobals = new HashSet<>(); // 调用此函数有可能读取的全局变量
        public Set<IceGlobalVariable> writeGlobals = new HashSet<>(); // 调用此函数有可能写入的全局变量
        public Set<IceFunction.IceFunctionParameter> writeParams = new HashSet<>(); // 调用此函数有可能修改的参数数组内容

        public PurenessInfo(Pureness pureness) {
            this.pureness = pureness;
        }

        public void setPureness(Pureness pureness) {
            if (pureness.ordinal() > this.pureness.ordinal()) {
                this.pureness = pureness;
            }
        }

        public Pureness getPureness() {
            return pureness;
        }
    }

    public final Map<IceFunction, PurenessInfo> functionPurenessInfo = new ConcurrentHashMap<>();

    public FunctionPureness(IceContext context) {
        context.addPassResult("functionPureness", functionPurenessInfo);
    }

    /**
     * 递归分析函数的纯性
     * @param currentFunction 当前分析的函数
     */
    private void analyzeFunction(IceFunction currentFunction) {
        if (functionPurenessInfo.containsKey(currentFunction)) {
            // 已经分析过了，直接返回
            return;
        }

        var currentPureness = functionPurenessInfo
                .computeIfAbsent(currentFunction, _ -> new PurenessInfo(Pureness.CONST));

        for (var block : currentFunction) {
            for (var instruction : block) {
                switch (instruction) {
                    case IceCallInstruction callInstruction -> {
                        // 调用指令
                        var targetFunction = callInstruction.getTarget();
                        if (targetFunction.equals(currentFunction)) continue; // 递归自身函数直接跳过
                        if (!functionPurenessInfo.containsKey(targetFunction)) {
                            // Note: 由于 SysY 的限制，不可能出现多个函数相互调用成环的情况，所以简化处理
                            analyzeFunction(targetFunction);
                        }

                        // target 函数已分析过，检查其纯性
                        var purenessInfo = functionPurenessInfo.get(targetFunction);
                        if (purenessInfo.pureness == Pureness.IMPURE) {
                            currentPureness.setPureness(Pureness.IMPURE); // 如果调用了不纯函数，则当前函数一定是不纯的
                            currentPureness.readGlobals.addAll(purenessInfo.readGlobals);
                            currentPureness.writeGlobals.addAll(purenessInfo.writeGlobals);
                        } else if (purenessInfo.pureness == Pureness.PURE) {
                            currentPureness.setPureness(Pureness.PURE); // 如果调用了纯函数，则当前函数有可能是 pure 的，但是一定不是 const
                        }

                        if (purenessInfo.pureness == Pureness.CONST) continue; // 如果是 const 函数，则不需要进一步分析参数

                        // 对于 pure 和 impure 函数，需要分析参数传递情况
                        var args = callInstruction.getArguments();
                        for (var i = 0; i < args.size(); ++i) {
                            var arg = args.get(i);
                            var isVAArgs = targetFunction instanceof IceExternFunction externFunction && externFunction.isVArgs();
                            var param = isVAArgs ? null : targetFunction.getParameters().get(i);
                            if (arg instanceof IceGEPInstruction gepInstruction) {
                                // 传递了数组参数
                                var base = gepInstruction.getBasePtr();
                                if (base instanceof IceGlobalVariable globalVariable) {
                                    if (purenessInfo.writeParams.contains(param)) { // FIXME: 对于可变参数默认认为不修改了每一个参数，目前只有putf所有这个假设是对的
                                        // 如果参数是数组且被写入，则当前函数不纯
                                        currentPureness.setPureness(Pureness.IMPURE);
                                        currentPureness.writeGlobals.add(globalVariable);
                                        currentPureness.readGlobals.add(globalVariable); // 不知道写入没有，作出保守假设
                                    } else {
                                        // 有可能被读取了全局变量，说明当前函数仅仅为 pure
                                        currentPureness.setPureness(Pureness.PURE);
                                        currentPureness.readGlobals.add(globalVariable);
                                    }
                                } else if (base instanceof IceFunction.IceFunctionParameter parameter) {
                                    if (parameter.getType().isArray()) { // 接力传递了数组参数
                                        // 如果是数组参数且被写入，等同于当前函数写入了参数，则当前函数不纯
                                        if (purenessInfo.writeParams.contains(param)) { // FIXME: 对于可变参数默认认为不修改了每一个参数，目前只有putf所有这个假设是对的
                                            currentPureness.setPureness(Pureness.IMPURE);
                                            currentPureness.writeParams.add(parameter);
                                        } else {
                                            // 仅仅读取数组参数，说明当前函数为 pure
                                            currentPureness.setPureness(Pureness.PURE);
                                        }
                                    }
                                }
                                // 把当前函数 alloca 出来的数组传递过去了，这个不影响当前函数的纯性
                            }
                        }
                    }

                    case IceLoadInstruction loadInstruction -> {
                        var source = loadInstruction.getSource();
                        if (source instanceof IceGEPInstruction gepInstruction) {
                            source = gepInstruction.getBasePtr();
                        }

                        if (source instanceof IceGlobalVariable globalVariable) {
                            // 全局变量读取
                            currentPureness.setPureness(Pureness.PURE); // 读取全局变量说明为纯函数不一定修改
                            currentPureness.readGlobals.add(globalVariable);
                        } else if (source instanceof IceFunction.IceFunctionParameter parameter) {
                            // 数组参数读取说明函数是pure的但是不是const，因为有可能相同的数组地址内容不同
                            if (parameter.getType().isArray()) {
                                currentPureness.setPureness(Pureness.PURE);
                            }
                        }
                    }

                    case IceStoreInstruction storeInstruction -> {
                        var target = storeInstruction.getTargetPtr();
                        if (target instanceof IceGEPInstruction gepInstruction) {
                            target = gepInstruction.getBasePtr();
                        }

                        if (target instanceof IceGlobalVariable globalVariable) {
                            // 全局变量写入
                            currentPureness.setPureness(Pureness.IMPURE); // 写入全局变量说明函数有副作用
                            currentPureness.writeGlobals.add(globalVariable);
                        } else if (target instanceof IceFunction.IceFunctionParameter parameter) {
                            // 参数写入
                            currentPureness.setPureness(Pureness.IMPURE); // 写入参数说明函数有副作用
                            currentPureness.writeParams.add(parameter);
                        }
                    }

                    default -> {
                        // 其他指令不影响纯性
                        // 例如：算术运算、比较等指令
                        // 这些指令不会修改全局状态或参数，所以不需要处理
                    }
                }
            }
        }
    }

    @Override
    public boolean run(IceUnit target) {
        for (var func : target.getFunctions()) {
            if (func instanceof IceExternFunction) {
                // 外部函数默认视为不纯
                functionPurenessInfo.computeIfAbsent(func,
                        externFunc -> switch (externFunc.getName()) {
                            case "getfarray", "getarray" -> {
                                var pureness = new PurenessInfo(Pureness.IMPURE);
                                pureness.writeParams.add(externFunc.getParameters().getFirst());
                                yield pureness;
                            }
                            default -> new PurenessInfo(Pureness.IMPURE);
                    });
            }
        }

        for (var func : target.getFunctions()) {
            if (func instanceof IceExternFunction) continue; // 外部函数不分析
            analyzeFunction(func);
        }
        return false;
    }
}
