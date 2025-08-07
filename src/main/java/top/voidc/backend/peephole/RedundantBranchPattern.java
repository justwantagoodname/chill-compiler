package top.voidc.backend.peephole;

import top.voidc.ir.IceBlock;
import top.voidc.ir.machine.IceMachineBlock;
import top.voidc.ir.machine.IceMachineInstruction;

import java.util.List;

public class RedundantBranchPattern implements PeepholePattern {

    private final List<IceBlock> BBs;

    public RedundantBranchPattern(List<IceBlock> BBs) {
        this.BBs = BBs;
    }

    @Override
    public int getWindowSize() {
        return 1;
    }

    @Override
    public List<IceMachineInstruction> matchAndApply(List<IceMachineInstruction> instructions) {
        assert instructions.size() == 1;
        IceMachineInstruction branch = instructions.getFirst();
        if (branch.getOpcode().equals("B") && branch.getOperands().size() == 1) {
            var targetBlock = (IceMachineBlock) branch.getOperands().getFirst();
            var targetBlockIndex = BBs.indexOf(branch.getParent());
            if (targetBlockIndex != -1 && targetBlockIndex + 1 < BBs.size() - 1) {
                if (BBs.get(targetBlockIndex + 1).equals(targetBlock)) {
                    // 如果此指令是跳转到当前目标块的下一个目标块，则可以删除当前指令
                    branch.destroy();
                    return List.of();
                }
            }
        }
        return null;
    }
}
