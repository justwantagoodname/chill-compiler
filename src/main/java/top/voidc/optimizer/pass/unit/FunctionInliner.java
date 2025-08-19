package top.voidc.optimizer.pass.unit;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUnit;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.Log;
import top.voidc.misc.algorithm.ReverseGraph;
import top.voidc.misc.algorithm.TopoSort;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.annotation.Qualifier;
import top.voidc.misc.ds.ChilletGraph;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

@Pass(group = {"O1"})
public class FunctionInliner implements CompilePass<IceUnit> {

    private static int INLINE_THRESHOLD = 300;

    private final ChilletGraph<IceFunction> callGraph;
    private final ChilletGraph<IceFunction> revCallGraph;
    private List<IceFunction> inlineWorkList;
    private IceUnit currentUnit;

    public FunctionInliner(@Qualifier("callGraph") ChilletGraph<IceFunction> callGraph) {
        this.callGraph = callGraph;
        this.revCallGraph = ReverseGraph.reverse(callGraph);
    }

    private void buildInlineWorklist() {
        // Note: SysY缺少前向声明，除了递归之外不可能存在环所以不用缩点了
//        var sccs = TarjanSCC.tarjan(this.callGraph);
//        Log.d(sccs.toString());
        // callGraph反映了调用关系，构造逆图反映被调用关系
        // 外部函数也没法内联 直接过滤掉
        inlineWorkList = TopoSort.topoSort(revCallGraph)
                .stream().filter(func -> !(func instanceof IceExternFunction)).toList();

//        Log.d(inlineWorkList.toString());
    }

    private boolean isRecursive(IceFunction function) {
        return callGraph.getNeighbors(callGraph.getNodeId(function))
                .contains(callGraph.getNodeId(function));
    }

    private int getBaseCode(IceFunction function) {
        var result = 0;
        for (var block : function) {
            result += 2;
            for (var instr : block) {
                result += 1;
                if (instr instanceof IceCallInstruction) result += 5;
            }
        }
        return result;
    }

    private int getBonus(IceFunction function) {
        var result = 0;
        // 常量参数
        result += function.getUsers().stream()
                .filter(val -> val instanceof IceCallInstruction call)
                .map(val -> (IceCallInstruction) val)
                .mapToInt(call ->
                        Math.toIntExact(call.getArguments().stream().filter(arg -> arg instanceof IceConstantData).count() * 100))
                .sum();

        // Single Call Site
        if (revCallGraph.getNeighbors(revCallGraph.getNodeId(function)).size() == 1) {
            result += 250;
        }
        return result;
    }

    private int getPenalty(IceFunction function) {
        return 0; // 暂时视为 0
    }

    private boolean shouldInline(IceFunction function) {
        if (function.getName().equals("main")) return false; // main函数显然无法内联
        if (isRecursive(function)) {
            return false; // 为了简单起见，首先不对递归函数进行内联
        }

        var baseCost = getBaseCode(function);
        var bonus = getBonus(function);
        var penalty = getPenalty(function);

        var inlineScore = baseCost - bonus + penalty;
        Log.d(
                String.format("Inline Candidate %s - base: %d  bonus: %d penalty: %d total: %d", function.getName(), baseCost, bonus, penalty, inlineScore));
        return inlineScore < INLINE_THRESHOLD;
    }

    /**
     * clone 原函数的基本块，<b>同时正确处理GEP参数</b>
     * @param inlineFunction 正在inline的函数
     * @param callerFunction 调用inline的函数
     * @param callSite 调用点
     * @return cloned 调用点列表
     */
    private List<IceBlock> deepCopyFunction(IceFunction inlineFunction,
                                         IceFunction callerFunction,
                                         IceCallInstruction callSite) {

        // 需要构建环境
        var clonedMapping = new HashMap<IceValue, IceValue>(); // 维护 old -> new 替换
        var clonedBlocks = new ArrayList<IceBlock>();

        for (var i = 0; i < inlineFunction.getParameters().size(); i++) {
            var param = inlineFunction.getParameters().get(i);
            var arg = callSite.getArguments().get(i);
            clonedMapping.put(param, arg);
        }

        for (var block : inlineFunction) {
            var cloneBlock = new IceBlock(callerFunction,
                    callerFunction.generateLabelName("inlined_" + inlineFunction.getName() + "_" + block.getName()));
            clonedMapping.put(block, cloneBlock);
            clonedBlocks.add(cloneBlock);
            for (var inst : block) {
                var clonedInst = inst.clone();
                assert clonedInst != inst;
                clonedInst.setParent(cloneBlock);
                clonedInst.setName(callerFunction.generateLocalValueName());
                cloneBlock.add(clonedInst);
                clonedMapping.put(inst, clonedInst);

                // 修正 GEP 类型，例如函数的参数是int* arr，但是内联后传入的类型是int[10] 这就需要添加一个0编译来正确维护类型
                if (clonedInst instanceof IceGEPInstruction gep
                        && gep.getBasePtr() instanceof IceFunction.IceFunctionParameter parameterArray
                        && !(clonedMapping.get(parameterArray) instanceof IceFunction.IceFunctionParameter)) { // 新的参数不是传过来的数组参数
                    assert parameterArray.getType().isPointer();
                    gep.addIndexAt(0, IceConstantData.create(0));
                }
            }
        }

        // remapping

        for (var block : clonedBlocks) {
            assert block.getFunction().equals(callerFunction);
            for (var inst : block) {
                for (var operand : List.copyOf(inst.getOperands())) {
                    if (clonedMapping.containsKey(operand)) {
                        inst.replaceOperand(operand, clonedMapping.get(operand));
                    }
                }
            }
        }

        return clonedBlocks;
    }

    /**
     * 实际内联函数
     * @param function 将当前函数的所有使用进行内联
     */
    private void doInline(IceFunction function) {
        for (var user : List.copyOf(function.getUsers())) {
            if (!(user instanceof IceCallInstruction call)) continue;
            assert call.getTarget().equals(function);

            // 查找所有的调用点
            var callerFunction = call.getParent().getFunction();
            var callSiteBlock = call.getParent();
            var callIndex = callSiteBlock.indexOf(call);

            Log.d("处理调用点 " + callerFunction + " call: " + call + " @ " + callSiteBlock.getName() + ":" + callIndex);

            // Step 1: 分割基本块

            var preCallInsts = List.copyOf(callSiteBlock.subList(0, callIndex));
            var postCallInsts = List.copyOf(callSiteBlock.subList(callIndex + 1, callSiteBlock.size()));

            Log.d(preCallInsts.toString());
            Log.d(postCallInsts.toString());

            // Step 2: 新建后继块
            var newSuccBlock = new IceBlock(callerFunction, callerFunction.generateLabelName(callSiteBlock.getName() + "_split"));
            postCallInsts.forEach(inst -> inst.moveTo(newSuccBlock));

            // 新后继块成了原后继实际意义上的前驱，需要相应替换 phi 指令
            for (var callSiteBlockUser : callSiteBlock.getUsers()) {
                if (callSiteBlockUser instanceof IcePHINode phi) {
                    phi.replaceOperand(callSiteBlock, newSuccBlock);
                    Log.d("Replace PHI" + phi.getTextIR());
                }
            }

            // Step 3: clone 原函数体
            var clonedBlocks = deepCopyFunction(function, callerFunction, call);

            // Step 4: jump 过去
            var entryBlock = clonedBlocks.getFirst();
            var branchEntry = new IceBranchInstruction(callSiteBlock, entryBlock);
            branchEntry.setParent(callSiteBlock);
            callSiteBlock.addLast(branchEntry);

            // Step 5: 处理返回值
            var exitBlocks = clonedBlocks.stream().filter(block -> block.successors().isEmpty()).toList();
            assert !exitBlocks.isEmpty();

            if (!function.getReturnType().isVoid()) {
                if (exitBlocks.size() > 1) {
                    // 需要使用 phi 函数
                    var phi = new IcePHINode(newSuccBlock, callerFunction.generateLocalValueName(), function.getReturnType());
                    phi.setValueToBeMerged(null);
                    for (var exitBlock : exitBlocks) {
                        var exitInst = (IceRetInstruction) exitBlock.getLast();
                        var exitValue = exitInst.getReturnValue().orElseThrow();
                        phi.addBranch(exitBlock, exitValue);
                    }
                    newSuccBlock.addFirst(phi);
                    call.replaceAllUsesWith(phi);
                } else {
                    var exitInst = (IceRetInstruction) exitBlocks.getFirst().getLast();
                    var exitValue = exitInst.getReturnValue().orElseThrow();
                    call.replaceAllUsesWith(exitValue);
                }
            }

            for (var block : exitBlocks) {
                block.getLast().destroy();
                var brEnd = new IceBranchInstruction(block, newSuccBlock);
                block.addLast(brEnd);
            }

            // Step 5: 删除原有 call 指令

            call.destroy();
        }
    }

    private void inlineFunction() {
        for (var func : inlineWorkList) {
            Log.d("Now Evaluating: " + func.getName());

            if (shouldInline(func)) {
                Log.d("内联: " + func.getName());
                doInline(func);
            }
        }
    }

    @Override
    public boolean run(IceUnit target) {
        this.currentUnit = target;
        buildInlineWorklist();
        inlineFunction();
        return false;
    }
}
