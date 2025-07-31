package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceConstantBoolean;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IcePHINode;

import top.voidc.misc.annotation.Pass;

import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

/**
 * 聪明疾旋鼬 CFG 简化器
 * 理论上应该会有可爱的鼬子删除无用的 block 和指令、合并无用的分支、合并无用的 phi 节点
 */
@Pass(
        group = {"O1"},
        parallel = true
)
public class SmartChilletSimplifyCFG implements CompilePass<IceFunction> {

    private static boolean removeDeadBlocks(List<IceBlock> allBlocks, IceFunction function) {
        boolean flag = false;

        Set<IceBlock> executableBlocks = new HashSet<>();
        Queue<IceBlock> queue = new ArrayDeque<>();
        executableBlocks.add(function.getEntryBlock());
        queue.add(function.getEntryBlock());

        while (!queue.isEmpty()) {
            IceBlock block = queue.poll();
            IceInstruction exitBlock = block.getLast();

            if (exitBlock instanceof IceBranchInstruction br) {
                if (!br.isConditional()) {
                    IceBlock target = br.getTargetBlock();
                    if (executableBlocks.add(target)) {
                        queue.add(target);
                    }
                } else {
                    IceBlock trueTarget = br.getTrueBlock(), falseTarget = br.getFalseBlock();
                    if (executableBlocks.add(trueTarget)) {
                        queue.add(trueTarget);
                    }
                    if (executableBlocks.add(falseTarget)) {
                        queue.add(falseTarget);
                    }
                }
            }
        }

        for (IceBlock block : allBlocks) {
            if (!executableBlocks.contains(block)) {
                block.destroy();
                flag = true;
            }
        }

        return flag;
    }

    /**
     * 合并成为一条直线的 block
     * 如果两个 block A 和 B 满足：
     * - A 只有一个后继 B
     * - B 只有一个前驱 A
     * - B 中没有 phi 节点
     * 则尝试将 B 中的指令移动到 A 中，然后删除块 B。
     * 这一过程也会将 B 的所有后继添加到 A 中。
     * 这个 method 将会从程序的入口 block 开始递归处理所有的 block。
     * 这里 A 为当前正在处理的 block，B 为当前 block 的下一个 block。
     * @param block 当前正在处理的 block
     */
    private static boolean mergeTrivialBlocks(Set<IceBlock> visited, IceBlock block) {
        // CFG 是不保证无环的
        if (!visited.add(block)) {
            return false;
        }

        boolean flag = false;
        while (block.getSuccessors().size() == 1) {
            IceBlock nextBlock = block.getSuccessors().getFirst();
            if (nextBlock.getPredecessors().size() == 1) {
                // A -> B(nextBlock)
                IceInstruction terminationInstr = block.getLast();

                if (!(terminationInstr instanceof IceBranchInstruction br) || br.isConditional() || !br.getTargetBlock().equals(nextBlock)) {
                    throw new RuntimeException("Error occurred in mergeTrivialBlocks: " + block + " -> " + nextBlock + " invalid CFG, " +
                            "outBlock: " + terminationInstr);
                }

                // 检查下一个 block 中是否有 phi 节点
                final boolean hasPHI = nextBlock.stream().anyMatch(in -> in instanceof IcePHINode);

                // 不应该有 phi 节点，因为对于这种一条直线的两个 block 来说，前面的block支配了后面的 block，因此后面的 block 绝对不是
                // 支配边界，因此不可能有 phi 节点
                if (hasPHI) break;
//                Log.should(!hasPHI, "Error occurred in mergeTrivialBlocks: " + block + " -> " + nextBlock + " has phi node");

                // 首先清除转跳指令
                terminationInstr.destroy();

                // 将下一个 block 中的指令移动到当前 block 中
                nextBlock.safeForEach(instruction -> instruction.moveTo(block));

                // 这一过程中，下一个 block 的所有后继会随着 outBlock 被添加到当前 block 中

                // 删除下一个 block
                assert nextBlock.isEmpty();

                // 直接用当前 block 替换下一个 block 的所有使用
                nextBlock.replaceAllUsesWith(block);
                flag = true;
                // 如果当前 block 合并成功了，尝试继续合并
            } else {
                break;
            }
        }

        // 递归处理所有的后继 block
        for (IceBlock successor : block.getSuccessors()) {
            if (!successor.equals(block)) {
                flag |= mergeTrivialBlocks(visited, successor);
            }
        }

        return flag;
    }

    /**
     * 简化所有的 branch 指令
     * - 如果分支的条件是常量，则直接跳转到目标 block
     * - 如果条件型分支的两个目标相同，则合并
     *
     * @param function 要处理的函数
     */
    private static boolean simplifyBranch(IceFunction function) {
        boolean flag = false;
        for (IceBlock block : function.getBlocks()) {
            for (int i = 0; i < block.size(); ++i) {
                IceInstruction instruction = block.get(i);
                if (!(instruction instanceof IceBranchInstruction br) || !br.isConditional()) {
                    continue;
                }

                IceValue cond = br.getCondition();
                if (cond instanceof IceConstantBoolean condition) {
                    IceBlock target = condition.getValue() == 1 ? br.getTrueBlock() : br.getFalseBlock();

                    // 删除原来的分支指令
                    br.destroy();

                    // 创建新的分支指令，会自动添加 successor
                    IceBranchInstruction newBr = new IceBranchInstruction(block, target);

                    // 替换这条指令
                    block.add(i, newBr);
                    flag = true;
                } else if (br.getTrueBlock() == br.getFalseBlock()) {

                    br.destroy();

                    // 如果两个目标相同，则直接跳转到目标 block
                    IceBranchInstruction newBr = new IceBranchInstruction(block, br.getTrueBlock());

                    // 替换这条指令
                    block.add(i, newBr);
                    flag = true;
                }
            }
        }
        return flag;
    }

    private static boolean simplifyPHINode(IceFunction function) {
        function.getBlocks().forEach(block ->
                block.stream().filter(instruction -> instruction instanceof IcePHINode)
                .map(instruction -> (IcePHINode) instruction).toList()
                .forEach(phi -> {
                    // 如果当前 phi 节点没有分支，则报错
                    if (phi.getBranchCount() == 0) {
                        throw new RuntimeException("Error occurred in simplifyPHINode: " + phi + " has no branch");
                    } else if (phi.getBranchCount() == 1) {
                        // 如果只有一个分支，则替换后删除
                        final var value = phi.getBranchValueOnIndex(0);
                        phi.replaceAllUsesWith(value);
                        phi.destroy();
                        return;
                    }

                    IceValue firstBranch = phi.getBranchValueOnIndex(0);
                    boolean removable = true;
                    if (firstBranch instanceof IceConstant) {
                        // 如果 phi 的第一个分支是常量，则检查剩下的是否都为常量且相等
                        for (int j = 1; j < phi.getBranchCount(); ++j) {
                            IceValue branch = phi.getBranchValueOnIndex(j);
                            if (!(branch instanceof IceConstant constant) || !constant.equals(firstBranch)) {
                                removable = false;
                                break;
                            }
                        }
                    } else {
                        // 如果第一个分支不是常量，则检查剩下的是否都相等
                        // SSA 形式中的值可以直接通过地址判断是否相等
                        for (int j = 1; j < phi.getBranchCount(); ++j) {
                            IceValue branch = phi.getBranchValueOnIndex(j);
                            if (branch != firstBranch) {
                                removable = false;
                                break;
                            }
                        }
                    }

                    if (removable) {
                        // 如果所有的分支都相等，则直接替换
                        phi.replaceAllUsesWith(firstBranch);
                        phi.destroy();
                    }
                }));
        return false;
    }

    /**
     * 移除没有使用的二元指令
     * 如果 IceBinaryInstruction 没有 user, 那么它就是可以被移除的
     *
     * @param function 要处理的函数
     */
    private static void removeUnusedBinaryInstructions(IceFunction function) {
        Queue<IceBinaryInstruction> workList = new ArrayDeque<>();
        for (IceBlock block : function.getBlocks()) {
            for (IceInstruction instruction : block) {
                if (instruction instanceof IceBinaryInstruction binary) {
                    if (binary.getUsers().isEmpty()) {
                        workList.add(binary);
                    }
                }
            }
        }

        while (!workList.isEmpty()) {
            IceBinaryInstruction binary = workList.poll();

            for (IceValue operand : binary.getOperands()) {
                operand.removeUse(binary);
                if (operand instanceof IceBinaryInstruction && operand.getUsers().isEmpty()) {
                    workList.add((IceBinaryInstruction) operand);
                }
            }

            binary.destroy();
        }
    }

    private static boolean removeUnusedPHINode(IceFunction function) {
        boolean flag = false;
        for (IceBlock block : function.getBlocks()) {
            ArrayList<IcePHINode> list = new ArrayList<>();
            for (IceInstruction instruction : block) {
                if (!(instruction instanceof IcePHINode phi)) {
                    continue;
                }

                if (phi.getUsers().isEmpty()) {
                    list.add(phi);
                    flag = true;
                }
            }

            for (IcePHINode phi : list) {
                // 如果 phi 节点没有使用者，则直接删除
                phi.destroy();
            }
        }
        return flag;
    }

    @Override
    public boolean run(IceFunction target) {
        boolean flag = false;
        final var allBlocks = new ArrayList<>(target.getBlocks());

        flag |= simplifyBranch(target);
        flag |= removeDeadBlocks(allBlocks, target);
        flag |= simplifyPHINode(target);
        flag |= mergeTrivialBlocks(new HashSet<>(), target.getEntryBlock());
        removeUnusedBinaryInstructions(target);
        flag |= removeUnusedPHINode(target);

        return flag;
    }

    @Override
    public String getName() {
        return "Smart Chillet Simplify CFG";
    }
}
