package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IcePHINode;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.misc.Log;
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
@Pass(group = {"O0", "needfix"})
public class LivenessAnalysis implements CompilePass<IceFunction> {
    // 结果的Data
    public record LivenessInfo(Set<IceValue> use, Set<IceValue> def, Set<IceValue> in, Set<IceValue> out) {}

    // 计算时的Data
    private record LivenessData(BitSet use, BitSet def, BitSet in, BitSet out) {}

    public record LivenessResult(Map<IceFunction, Map<IceBlock, LivenessInfo>> livenessInfo) { }

    private final LivenessResult livenessResult = new LivenessResult(new ConcurrentHashMap<>());

    public LivenessAnalysis(IceContext context) {
        context.addPassResult(livenessResult);
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
    private BitSet getDef(Map<IceValue, Integer> valueId, IceBlock block) {
        final var def = new BitSet(valueId.size());
        for (var instruction : block) {
            // 对于全局变量不加入分析
            if (!(instruction instanceof IceStoreInstruction)) {
                if (!instruction.getType().isVoid()) {
                    def.set(valueId.get(instruction));
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
    private BitSet getUse(Map<IceValue, Integer> valueId, BitSet def, IceBlock block) {
        final var use = new BitSet(valueId.size());

        for (var instruction : block) {
            switch (instruction) {
                case IceBranchInstruction branch -> {
                    if (branch.isConditional()
                            && !(branch.getCondition() instanceof IceConstantData)
                            && !(branch.getCondition() instanceof IceGlobalVariable)
                            && !def.get(valueId.get(branch.getCondition()))) {
                        use.set(valueId.get(branch.getCondition()));
                    }
                }
                case IcePHINode phiNode -> {
                    // TODO: 先当作指令吧，应该先移除phi指令
                    for (var branchValue : phiNode.getBranches()) {
                        if (!(branchValue.value() instanceof IceConstantData)
                                && !(branchValue.value() instanceof IceFunction)
                                && !(branchValue.value() instanceof IceGlobalVariable)
                                && !def.get(valueId.get(branchValue.value()))) {
                            use.set(valueId.get(branchValue.value()));
                        }
                    }
                }
                default -> {
                    for (var operand : instruction.getOperands()) {
                        // 遇到的时候不在 Def(B) 中 且不是常量 不是函数
                        if (!(operand instanceof IceConstantData)
                                && !(operand instanceof IceFunction)
                                && !(operand instanceof IceGlobalVariable)
                                && !def.get(valueId.get(operand))) {
                            use.set(valueId.get(operand));
                        }
                    }
                }
            }
        }
        return use;
    }

    @Override
    public boolean run(IceFunction target) {
        final Map<IceValue, Integer> valueId = new HashMap<>();
        final List<IceValue> idValue = new ArrayList<>();

        final Map<IceBlock, LivenessData> blockInfo = new HashMap<>();
//        livenessResult.livenessInfo().put(target, blockInfo);

        final var blocks = target.getBlocks();

        for (var block : blocks) {
            for (var instruction : block) {
                if (instruction.getType().isVoid()) {
                    continue;
                }
                if (instruction instanceof IceStoreInstruction) {
                    // 对于全局变量不加入分析
                    continue;
                }
                idValue.add(block);
                valueId.put(instruction, idValue.size() - 1);
            }
        }

        var sum = 0;
        for (var block : blocks) {
            final var def = getDef(valueId, block);
            final var use = getUse(valueId, def, block);
            final var in = new BitSet(idValue.size());
            final var out = new BitSet(idValue.size());
            blockInfo.put(block, new LivenessData(use, def, in, out));
            sum += block.size();
        }
        Log.d("变量总数: " + sum + "blocks: " + blocks.size());

        /*
          out[B] = ∪ in[S]    // S 为 B 的所有后继块
          in[B]  = use[B] ∪ (out[B] - def[B])
         */
        boolean changed;
        do {
            Log.d("iteration once");
            changed = false;
            for (var block : blocks) {
//                Log.d("========");
//                Log.d("block: " + block.getName());
//                Log.d("successors: " + block.getSuccessors());
//                Log.d("predecessors: " + block.getPredecessors());
//                Log.d("def: " + blockInfo.get(block).def());
//                Log.d("use: " + blockInfo.get(block).use());
//                Log.d("in: " + blockInfo.get(block).in());
//                Log.d("out: " + blockInfo.get(block).out());

                final var use = blockInfo.get(block).use();
                final var def = blockInfo.get(block).def();

                final var out = blockInfo.get(block).out();

                final var in = blockInfo.get(block).in();

                final var newOut = new BitSet(idValue.size());
                // 计算 out[B]
                for (var successor : block.getSuccessors()) {
                    final var successorInfo = blockInfo.get(successor);
                    newOut.or(successorInfo.in());
                }

                if (!newOut.equals(out)) {
                    changed = true;
                }

                final var newIn = new BitSet(idValue.size());
                newIn.or(use);

                // 计算 out[B] - def[B]
                newOut.andNot(def);
                newIn.or(newOut);
                if (!newIn.equals(in)) {
                    changed = true;
                }
            }
        } while (changed);

        return false;
    }
}
