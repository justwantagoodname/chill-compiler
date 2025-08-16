package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.annotation.Qualifier;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.optimizer.pass.unit.FunctionPureness;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 聪明疾旋鼬删除无用的值
 * 主要是删除那些没有被使用的值
 * 这可能会导致一些指令被删除
 */
@Pass(group = {"O0"})
public class SmartChilletDeleteUnusedValue implements CompilePass<IceFunction> {

    private final IceContext context;
    private final Map<IceFunction, FunctionPureness.PurenessInfo> purenessInfoMap;

    public SmartChilletDeleteUnusedValue(IceContext context, @Qualifier("functionPureness") Map<IceFunction, FunctionPureness.PurenessInfo> purenessInfoMap) {
        this.purenessInfoMap = purenessInfoMap;
        this.context = context;
    }

    /**
     * 检查指令是否有副作用
     * 副作用指令包括：
     * 1. 修改全局变量
     * 2. 调用函数(非纯函数)
     * 3. 存入数组或指针
     */
    private boolean hasSideEffect(IceInstruction instruction) {
        if (instruction instanceof IceStoreInstruction // 修改全局变量和数组都在这里面了 加载不算
                || instruction instanceof IceRetInstruction
                || instruction instanceof IceBranchInstruction) return true;

        // PURE 和 CONST 函数是没有副作用的，但是可能依赖环境状态

        return instruction instanceof IceCallInstruction call
                && purenessInfoMap.get(call.getTarget()).getPureness() == FunctionPureness.Pureness.IMPURE;
    }

    private void removeUnusedValue(Set<IceInstruction> deleteInstructions, IceInstruction instruction) {
        if (deleteInstructions.contains(instruction)) return; // 如果已经在删除列表中，直接返回

        // 假删除指令
        deleteInstructions.add(instruction);

        // 递归删除所有未使用的值
        for (var value : instruction.getOperands()) {
            if (value instanceof IceInstruction inst && !hasSideEffect(inst)
                    && inst.getUsers().size() == 1 && inst.getUsers().getFirst().equals(instruction)) { // 没有副作用并且仅被当前指令使用
                removeUnusedValue(deleteInstructions, inst);
            }
        }
    }

    @Override
    public boolean run(IceFunction target) {
        final var deleteInstructions = new HashSet<IceInstruction>();
        for (var block : target) {
            block.safeForEach(instruction -> {
                if (instruction.getParent() == null) {
                    // 如果指令没有父节点，说明它已经被删除了
                    return;
                }
                switch (instruction) {
                    case IceStoreInstruction _  -> {}
                    case IceIntrinsicInstruction intrinsic -> {
                        if (intrinsic.getIntrinsicName().equals(IceIntrinsicInstruction.MEMCPY)
                                || intrinsic.getIntrinsicName().equals(IceIntrinsicInstruction.MEMSET)) {
                            // memcpy 和 memset 是有副作用的，但是如果其中的数组仅被这个memcpy和memset使用，那就可以删除
                            var dest = (IceAllocaInstruction) intrinsic.getOperand(0);
                            assert dest != null;
                            if (dest.getUsers().size() == 1 && dest.getUsers().getFirst().equals(intrinsic)) {
                                // 如果这个数组仅被这个memcpy或memset使用，那么就可以删除
                                removeUnusedValue(deleteInstructions, intrinsic);
                                removeUnusedValue(deleteInstructions, dest);

                                if (intrinsic.getIntrinsicName().equals(IceIntrinsicInstruction.MEMCPY)) {
                                    // 对于 memcpy，如果目标数组被删除了，那么源数组也可以删除
                                    var src = (IceConstant) intrinsic.getOperand(1);
                                    context.getCurrentIR().removeGlobalDecl(src);
                                }
                            }
                        }
                    }
                    case IceBranchInstruction _ , IceRetInstruction _  -> {}
                    case IceCallInstruction call -> {
                        var pureInfo = purenessInfoMap.get(call.getTarget());
                        if (pureInfo.getPureness() != FunctionPureness.Pureness.IMPURE) {
                            // 如果是纯函数调用，那么可以删除这个调用
                            if (call.getUsers().isEmpty()) {
                                // 如果这个调用没有被使用且没有副作用，那么就可以删除
                                removeUnusedValue(deleteInstructions, call);
                            }
                        }
                        // 如果是非纯函数调用，那么不能删除
                        // 这里不做任何处理，保留这个调用
                    }
                    default -> {
                        assert !instruction.getType().isVoid();
                        if (instruction.getUsers().isEmpty() && !hasSideEffect(instruction)) {
                            // 如果这个指令没有被使用且没有副作用，那么就可以删除，这里执行递归删除
                            removeUnusedValue(deleteInstructions, instruction);
                        }
                    }
                }
            });
        }

        deleteInstructions.forEach(instruction -> {
            if (instruction.getParent() != null) {
                instruction.getParent().remove(instruction);
            }
            instruction.setParent(null);
            instruction.destroy();
        });
        return !deleteInstructions.isEmpty();
    }
}
