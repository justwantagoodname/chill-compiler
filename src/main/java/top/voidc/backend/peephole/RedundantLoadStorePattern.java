package top.voidc.backend.peephole;

import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import java.util.List;

public class RedundantLoadStorePattern implements PeepholePattern {

    @Override
    public int getWindowSize() {
        return 2;
    }

    @Override
    public List<IceMachineInstruction> matchAndApply(List<IceMachineInstruction> instructions) {
        assert instructions.size() == 2;
        IceMachineInstruction storeInst = instructions.get(0);
        IceMachineInstruction loadInst = instructions.get(1);


        if (!loadInst.getOpcode().equals("LDR") || !storeInst.getOpcode().equals("STR")
                || loadInst.getOperands().size() != 2 || storeInst.getOperands().size() != 2) return null;

        if (((IceMachineRegister.RegisterView) storeInst.getSourceOperands().getFirst()).getRegister()
                .equals(loadInst.getResultReg().getRegister())) { // 存储的目标是加载的目标

            var storeSlot = storeInst.getOperands().get(1);
            var loadSlot = loadInst.getOperands().get(1);
            if (!storeSlot.equals(loadSlot)) return null; // 如果存储和加载的地址不一致，则不匹配

            // 冗余的加载和存储指令，直接删除
            loadInst.destroy();
            return List.of(storeInst); // 仅保留store
        }
        return null; // 不匹配或不需要应用
    }
}
