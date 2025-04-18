package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
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
import top.voidc.optimizer.pass.Helper;

import java.util.*;

/**
 * 聪明疾旋鼬 CFG 简化器
 * 会尝试删除无用的 block 和指令、合并无用的分支、合并无用的 phi 节点
 */
@Pass
public class SmartChilletSimplifyCFG implements CompilePass<IceFunction> {

    private static ArrayList<IceBlock> allBlocks;

    private static void removeDeadBlocks(IceFunction function) {
        Set<IceBlock> executableBlocks = new HashSet<>();
        Queue<IceBlock> queue = new ArrayDeque<>();
        executableBlocks.add(function.getEntryBlock());
        queue.add(function.getEntryBlock());

        while (!queue.isEmpty()) {
            IceBlock block = queue.poll();
            IceInstruction exitBlock = block.getInstructions().get(block.getInstructions().size() - 1);

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
                Helper.removeBlock(block);
            }
        }
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
     *
     * @param block 当前正在处理的 block
     */
    private static void mergeTrivialBlocks(IceBlock block) {
        if (block.getSuccessors().size() == 1) {
            IceBlock nextBlock = block.getSuccessors().get(0);
            if (nextBlock.getPredecessors().size() == 1) {
                IceInstruction outBlock = block.getInstructions().get(block.getInstructions().size() - 1);

                if (!(outBlock instanceof IceBranchInstruction br) || br.isConditional() || br.getTargetBlock() != nextBlock) {
                    throw new RuntimeException("Error occurred in mergeTrivialBlocks: " + block + " -> " + nextBlock + " invalid CFG, " +
                            "outBlock: " + outBlock);
                }

                // 检查下一个 block 中是否有 phi 节点
                boolean hasPHI = false;
                for (IceInstruction inst : nextBlock.getInstructions()) {
                    if (inst instanceof IcePHINode) {
                        hasPHI = true;
                        break;
                    }
                }

                if (!hasPHI) {
                    // 将下一个 block 中的指令移动到当前 block 中
                    List<IceInstruction> instructions = nextBlock.getInstructions();
                    for (int i = 0; i < instructions.size(); ++i) {
                        instructions.get(i).moveTo(block);
                    }
                    // 这一过程中，下一个 block 的所有后继会随着 outBlock 被添加到当前 block 中

                    // 删除当前 block 的分支指令
                    block.removeInstruction(br);
                    // 删除下一个 block
                    Helper.removeBlock(nextBlock);

                    // 如果当前 block 合并成功了，说明可以继续合并
                    // 递归处理当前 block 的下一个 block
                    mergeTrivialBlocks(block);
                }
            }
        } else {
            // 递归处理所有的后继 block
            for (IceBlock successor : block.getSuccessors()) {
                mergeTrivialBlocks(successor);
            }
        }
    }

    /**
     * 简化所有的 branch 指令
     * - 如果分支的条件是常量，则直接跳转到目标 block
     * - 如果条件型分支的两个目标相同，则合并
     *
     * @param function 要处理的函数
     */
    private static void simplifyBranch(IceFunction function) {
        for (IceBlock block : function.getBlocks()) {
            List<IceInstruction> instructions = block.getInstructions();
            for (int i = 0; i < instructions.size(); ++i) {
                IceInstruction instruction = instructions.get(i);
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
                    instructions.add(i, newBr);
                } else if (br.getTrueBlock() == br.getFalseBlock()) {

                    br.destroy();

                    // 如果两个目标相同，则直接跳转到目标 block
                    IceBranchInstruction newBr = new IceBranchInstruction(block, br.getTrueBlock());

                    // 替换这条指令
                    instructions.add(i, newBr);
                }
            }
        }
    }

    private static void simplifyPHINode(IceFunction function) {
        for (IceBlock block : function.getBlocks()) {
            List<IceInstruction> instructions = block.getInstructions();
            for (int i = 0; i < instructions.size(); ++i) {
                IceInstruction instruction = instructions.get(i);
                if (!(instruction instanceof IcePHINode phiNode)) {
                    continue;
                }

                // 如果当前 phi 节点没有分支，则报错
                if (phiNode.getBranchCount() == 0) {
                    throw new RuntimeException("Error occurred in simplifyPHINode: " + phiNode + " has no branch");
                }

                IceValue firstBranch = phiNode.getBranchValueOnIndex(0);
                boolean removable = true;
                if (firstBranch instanceof IceConstant) {
                    // 如果 phi 的第一个分支是常量，则检查剩下的是否都为常量且相等
                    for (int j = 1; j < phiNode.getBranchCount(); ++j) {
                        IceValue branch = phiNode.getBranchValueOnIndex(j);
                        if (!(branch instanceof IceConstant constant) || !constant.equals(firstBranch)) {
                            removable = false;
                            break;
                        }
                    }
                } else {
                    // 如果第一个分支不是常量，则检查剩下的是否都相等
                    // SSA 形式中的值可以直接通过地址判断是否相等
                    for (int j = 1; j < phiNode.getBranchCount(); ++j) {
                        IceValue branch = phiNode.getBranchValueOnIndex(j);
                        if (branch != firstBranch) {
                            removable = false;
                            break;
                        }
                    }
                }

                if (removable) {
                    List<IceUser> users = phiNode.getUsersList();
                    for (IceUser user : users) {
                        if (user instanceof IceInstruction instruction1) {
                            instruction1.replaceOperand(phiNode, firstBranch);
                        }
                    }

                    // 删除这个 phi 节点
                    instructions.remove(i);
                    --i;
                }
            }
        }
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
            for (IceInstruction instruction : block.getInstructions()) {
                if (instruction instanceof IceBinaryInstruction binary) {
                    if (binary.getUsersList().isEmpty()) {
                        workList.add(binary);
                    }
                }
            }
        }

        while (!workList.isEmpty()) {
            IceBinaryInstruction binary = workList.poll();
            IceBlock parent = binary.getParent();

            for (IceValue operand : binary.getOperands()) {
                operand.removeUse(binary);
                if (operand instanceof IceBinaryInstruction && operand.getUsersList().isEmpty()) {
                    workList.add((IceBinaryInstruction) operand);
                }
            }

            parent.removeInstruction(binary);
        }
    }

    @Override
    public void run(IceFunction target) {
        allBlocks = new ArrayList<>(target.getBlocks());
        allBlocks.addAll(target.getBlocks());

        simplifyBranch(target);
        removeDeadBlocks(target);
        simplifyPHINode(target);
        mergeTrivialBlocks(target.getEntryBlock());
        removeUnusedBinaryInstructions(target);
    }

    @Override
    public String getName() {
        return "Smart Chillet Simplify CFG";
    }
}
