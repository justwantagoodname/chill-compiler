package top.voidc.optimizer.pass.function;

import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.HashSet;
import java.util.Set;

/**
 * 聪明疾旋鼬删除无用的值
 * 主要是删除那些没有被使用的值
 * 这可能会导致一些指令被删除
 */
@Pass(group = {"O0"})
public class SmartChilletDeleteUnusedValue implements CompilePass<IceFunction> {
    /**
     * 检查指令是否有副作用
     * 副作用指令包括：
     * 1. 修改全局变量
     * 2. 调用函数(非纯函数)
     * 3. 存入数组或指针
     */
    private boolean hasSideEffect(IceInstruction instruction) {
        return instruction instanceof IceStoreInstruction // 修改全局变量和数组都在这里面了 加载不算
                || instruction instanceof IceCallInstruction // TODO: 做完纯函数分析后应用实际的情况
                || instruction instanceof IceRetInstruction
                || instruction instanceof IceBranchInstruction;
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
                    case IceIntrinsicInstruction _ -> {} // TODO: 做完纯函数分析后应用实际的情况
                    case IceBranchInstruction _ , IceRetInstruction _  -> {}
                    case IceCallInstruction call -> {
                        if (false) { // FIXME: 先认为所有的函数调用是不纯的
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
