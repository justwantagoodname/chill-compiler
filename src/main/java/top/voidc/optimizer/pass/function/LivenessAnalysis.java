package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 变量活跃性分析
 * 使用方程进行迭代
 * out[B] = ∪ in[S]    // S 为 B 的所有后继块
 * in[B]  = use[B] ∪ (out[B] - def[B])
 */
@Pass(group = "needfix")
public class LivenessAnalysis implements CompilePass<IceFunction> {
    public record LivenessInfo(Set<IceValue> use, Set<IceValue> def, Set<IceValue> in, Set<IceValue> out) {
        public LivenessInfo() {
            this(new HashSet<>(), new HashSet<>(), new HashSet<>(), new HashSet<>());
        }
    }

    public record LivenessResult(Map<IceFunction, Map<IceBlock, LivenessInfo>> livenessInfo) {
    }

    private final IceContext context;
    private final LivenessResult livenessResult = new LivenessResult(new ConcurrentHashMap<>());

    public LivenessAnalysis(IceContext context) {
        this.context = context;
        this.context.addPassResult(livenessResult);
    }

    @Override
    public String getName() {
        return "LivenessAnalysis";
    }

    /**
     * Def(B) 是所有定义在基本块 B 中的变量（定义先于使用）
     * <br>
     * 由于是 SSA 形式的 IR，变量名是唯一的，是所有左边的变量
     * @param block 基本块
     * @return Def(block)
     */
    private Set<IceValue> getDef(IceBlock block) {
        final var def = new HashSet<IceValue>();
        for (var instruction : block.getInstructions()) {
            // 对于全局变量特殊处理
            if (Objects.requireNonNull(instruction) instanceof IceStoreInstruction storeInst) {
                def.add(storeInst.getTargetPtr());
            } else {
                if (!instruction.getType().isVoid()) {
                    def.add(instruction);
                }
            }
        }
        return def;
    }

    /**
     * Use(B) 是所有在基本块 B 中使用的变量（使用先于定义）
     * <br>
     * 由于是 SSA 形式的 IR，变量名是唯一的，是所有右边的变量且使用时不在 Def(B) 中
     * @param def Def(B)
     * @param block 基本块
     * @return Use(B)
     */
    private Set<IceValue> getUse(Set<IceValue> def, IceBlock block) {
        final var use = new HashSet<IceValue>();
        for (var instruction : block.getInstructions()) {
            if (Objects.requireNonNull(instruction) instanceof IceBranchInstruction branchInst) {
                // 对于分支指令，使用的变量是条件表达式
                if (branchInst.isConditional() && !(branchInst.getCondition() instanceof IceConstantData)) {
                    use.add(branchInst.getCondition());
                }
            } else {
                for (var operand : instruction.getOperands()) {
                    // 遇到的时候不在 Def(B) 中 且不是常量
                    if (!def.contains(operand) && !(operand instanceof IceConstantData)) {
                        use.add(operand);
                    }
                }
            }
        }
        return use;
    }

    @Override
    public boolean run(IceFunction target) {
        final Map<IceBlock, LivenessInfo> blockInfo = new HashMap<>();
        livenessResult.livenessInfo().put(target, blockInfo);
        final var blocks = target.getBlocks();

        for (var block : blocks) {
            final var def = getDef(block);
            final var use = getUse(def, block);
            final var in = new HashSet<IceValue>();
            final var out = new HashSet<IceValue>();
            blockInfo.put(block, new LivenessInfo(use, def, in, out));
        }

        /*
          out[B] = ∪ in[S]    // S 为 B 的所有后继块
          in[B]  = use[B] ∪ (out[B] - def[B])
         */
        boolean changed;
        do {
            changed = false;
            for (var block : blocks) {
                final var use = blockInfo.get(block).use();
                final var def = blockInfo.get(block).def();

                final var out = blockInfo.get(block).out();
                final var oldOutSize = out.size();
//                out.clear();
                block.getSuccessors().stream()
                        .map(successorBlock -> blockInfo.get(successorBlock).in())
                        .forEach(out::addAll);
                if (out.size() != oldOutSize) {
                    changed = true;
                }

                final var in = blockInfo.get(block).in();
                final var oldInSize = in.size();
//                in.clear();
                in.addAll(use);
                final var outMinusDef = new HashSet<>(out);
                outMinusDef.removeAll(def);
                in.addAll(outMinusDef);

                if (in.size() != oldInSize) {
                    changed = true;
                }
            }
        } while (changed);

        return false;
    }
}
