package top.voidc.backend;

import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

@Pass(group = {"O1", "backend"})
public class GraphColoringAllocateRegister implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {
    @Override
    public boolean run(IceMachineFunction target) {
        if (!hasVirtualRegAlloc(target)) {
            return false;
        }
        return false;
    }

    private boolean hasVirtualRegAlloc(IceMachineFunction mf) {
        for (var block : mf) {
            for (var iceInstruction : block) {
                var instr = (IceMachineInstruction)  iceInstruction;

                for (var operand : instr.getOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView registerView) {
                        if (registerView.getRegister().isVirtualize()) {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    @Override
    public String getArchitecture() {
        return "armv8-a";
    }

    @Override
    public String getABIName() {
        return "linux-gnu-glibc";
    }

    @Override
    public int getBitSize() {
        return 64;
    }
}
