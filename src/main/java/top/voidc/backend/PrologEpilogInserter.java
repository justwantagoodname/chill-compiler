package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class PrologEpilogInserter implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    // 直到这次被调用前，使用过的 caller-save 寄存器集合
    private Set<IceMachineRegister> usedRegs;

    private static boolean isCallerSaved(IceMachineRegister reg) {
        return switch (reg.getName()) {
            case "x9", "x10", "x11", "x12", "x13", "x14", "x15" -> true; // Caller-saved registers
            default -> false; // Other registers are callee-saved
        };
    }

    @Override
    public boolean run(IceMachineFunction target) {
        if (!(target instanceof ARM64Function mf)) {
            throw new IllegalArgumentException("CallerSaver only supports ARM64Function.");
        }

        usedRegs = new HashSet<>();

        for (var block : target) {
            for (int i = 0; i < block.size(); ++i) {
                if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why is there a non-machine instruction in a machine function?");
                }

//                if (inst.getO)

                    for (var operand : inst.getOperands()) {
                        if (operand instanceof IceMachineRegister reg) {
                            assert !reg.isVirtualize() : "Virtual registers should not be present in machine instructions after register allocation.";
                            if (isCallerSaved(reg)) {
                                // 如果是 caller-saved 寄存器，记录下来
                                usedRegs.add(reg);
                            }
                        }
                    }
            }
        }

        return true;
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
