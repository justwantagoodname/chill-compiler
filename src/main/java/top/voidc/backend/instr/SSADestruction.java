package top.voidc.backend.instr;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceCopyInstruction;
import top.voidc.ir.ice.instruction.IcePHINode;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class SSADestruction implements CompilePass<IceFunction> {

    private final Map<IceBlock, Set<IceBlock>> criticalEdges = new HashMap<>();

    /**
     * 在CFG中，如果边的源结点具有多个后继结点，⽽边的⽬标结点具有多个前趋结点，则称这样边为关键边
     * @param fromBlock 源节点
     * @param toBlock 目标节点
     * @return 是否存在关键边
     */
    private boolean isCriticalEdge(IceBlock fromBlock, IceBlock toBlock) {
        return fromBlock.successors().size() > 1
                && toBlock.predecessors().size() > 1
                && fromBlock.successors().contains(toBlock);
    }

    /**
     * 将所有有必要的关键边进行分裂以便插入复制指令
     */
    private void splitCriticalEdge(List<IceBlock> blocks) {
        // 标记所有关键边，只有存在phi节点时才标记
        for (var block : blocks) {
            for (var inst: block) {
                if (inst instanceof IcePHINode phiNode) {
                    for (var branches : phiNode.getBranches()) {
                        if (isCriticalEdge(branches.block(), block)) {
                            criticalEdges.computeIfAbsent(branches.block(), _ -> new HashSet<>())
                                    .add(block);
                        }
                    }
                } else break;
            }
        }

        // 分裂关键边（插入新基本块）
        for (var entry : criticalEdges.entrySet()) {
            var fromBlock = entry.getKey();
            for (var toBlock : entry.getValue()) {
                // 创建新块
                var termInstr = fromBlock.getLast();
                if (termInstr instanceof IceBranchInstruction branch) {
                    assert branch.isConditional();
                    var tempBlock = new IceBlock(fromBlock.getFunction());

                    // 维护CFG关系
                    branch.replaceOperand(toBlock, tempBlock);
                    var jumpToTarget = new IceBranchInstruction(tempBlock, toBlock);
                    tempBlock.addInstruction(jumpToTarget);

                    // 替换原来的操作数
                    for (var instr : toBlock) {
                        if (instr instanceof IcePHINode phiNode) {
                            phiNode.replaceOperand(fromBlock, tempBlock);
                        } else break;
                    }

                } else throw new IllegalStateException("最后一条指令不是分支指令: " + termInstr);
            }
        }
    }

    private void removePhiNodes(IceBlock block) {
        for (var instr : block) {
            if (instr instanceof IcePHINode phiNode) {
                for (var branches : phiNode.getBranches()) {
                    var fromBlock = branches.block();
                    var copyValue = branches.value();
                    fromBlock.addFirst(new IceCopyInstruction(fromBlock, phiNode, copyValue));
                }
                phiNode.setEliminated(true);
            } else break;
        }
    }

    @Override
    public boolean run(IceFunction target) {
        var isChanged = false;
        var blocks = target.blocks();
        splitCriticalEdge(blocks);
        for (var block : blocks) {
            if (block.stream().anyMatch(inst -> inst instanceof IcePHINode)) {
                removePhiNodes(block);
                isChanged = true;
            }
        }
        return isChanged;
    }
}
