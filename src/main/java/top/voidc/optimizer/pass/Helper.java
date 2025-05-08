package top.voidc.optimizer.pass;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IcePhiInstruction;

public class Helper {
    /**
     * 从 CFG 上删除一个 block
     *
     * @param block 要删除的 block
     */
    public static void removeBlock(IceBlock block) {
        // 枚举所有的 successor
        for (IceBlock successor : block.getSuccessors()) {
            // block 会影响 successor 中的 phi 节点
            for (IceInstruction inst : successor.getInstructions()) {
                if (inst instanceof IcePhiInstruction phiNode) {
                    // 在这个 phi 节点中，删除这个分支
                    phiNode.removeBranch(block);
                }
            }

            // 删除 use 关系
            block.removeSuccessor(successor);
        }

        // 在所有的 predecessor 中删除这个 block
        for (IceBlock predecessor : block.getPredecessors()) {
            predecessor.removeSuccessor(block);
        }
    }
}
