package top.voidc.backend.peephole;

import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import java.util.List;

// 冗余
public class RedundantMovePattern implements PeepholePattern {
    @Override
    public int getWindowSize() {
        return 1;
    }

    @Override
    public List<IceMachineInstruction> matchAndApply(List<IceMachineInstruction> instructions) {
        assert instructions.size() == 1;
        IceMachineInstruction instruction = instructions.getFirst();
        if ((instruction.getOpcode().equals("MOV") || instruction.getOpcode().equals("FMOV"))
                && instruction.getOperands().size() == 2 && instruction.getOperands().get(1) instanceof IceMachineRegister.RegisterView srcView) {
            var dst = instruction.getResultReg().getRegister();
            var src = srcView.getRegister();
            if (dst.equals(src)) {
                // 冗余的移动指令，直接删除
                instruction.destroy();
                return List.of(); // 返回空列表表示删除了该指令
            }
        }
        return null;
    }
}
