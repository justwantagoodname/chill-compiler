package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class RegSaver implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    private static boolean isCallerSaved(IceMachineRegister reg) {
        String name = reg.getName();
        if (name.equals("zr")) return false;
        int id = Integer.parseInt(name);
        return switch (id) {
            case 9, 10, 11, 12, 13, 14, 15 -> true; // Caller-saved registers
            default -> false; // Other registers are callee-saved
        };
    }

    private static boolean isCalleeSaved(IceMachineRegister reg) {
        String name = reg.getName();
        if (name.equals("zr")) return false;
        int id = Integer.parseInt(name);
        return switch (id) {
            case 19, 20, 21, 22, 23, 24, 25, 26, 27 -> true; // Callee-saved registers
            default -> false; // Other registers are caller-saved
        };
    }

    @Override
    public boolean run(IceMachineFunction target) {
        if (!(target instanceof ARM64Function mf)) {
            throw new IllegalArgumentException("CallerSaver only supports ARM64Function.");
        }

        saveCallerSavedRegs(mf);
        saveCalleeSavedRegs(mf);

        return true;
    }

    private void saveCallerSavedRegs(ARM64Function mf) {

        // 直到这次被调用前，使用过的 caller-save 寄存器集合
        Set<IceMachineRegister> usedRegs = new HashSet<>();
        Map<IceMachineRegister, IceStackSlot> regSlots = new HashMap<>();

        for (var block : mf) {
            for (int i = 0; i < block.size(); ++i) {
                if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why is there a non-machine instruction in a machine function?");
                }

                if (inst.getOpcode().equals("BL")) {
                    for (var reg : usedRegs) {
                        IceMachineInstruction str = new ARM64Instruction("STR {src}, {local:dst}", reg.createView(reg.getType()), regSlots.get(reg));
                        block.add(i, str);
                        ++i;
                    }

                    // 越过 BL 指令
                    ++i;

                    for (var reg : usedRegs) {
                        IceMachineInstruction ldr = new ARM64Instruction("LDR {src}, {local:dst}", reg.createView(reg.getType()), regSlots.get(reg));
                        block.add(i, ldr);
                        ++i;
                    }
                    continue;
                }

                for (var operand : inst.getOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView rv) {
                        IceMachineRegister reg = rv.getRegister();
                        assert !reg.isVirtualize() : "Virtual registers should not be present in machine instructions after register allocation.";
                        if (isCallerSaved(reg)) {
                            // 如果是 caller-saved 寄存器，记录下来
                            usedRegs.add(reg);
                            regSlots.putIfAbsent(reg, mf.allocateVariableStackSlot(reg.getType()));
                        }
                    }
                }
            }
        }
    }

    private void saveCalleeSavedRegs(ARM64Function mf) {
        // 直到这次被调用前，使用过的 callee-save 寄存器集合
        Set<IceMachineRegister> usedRegs = new HashSet<>();
        Map<IceMachineRegister, IceStackSlot> regSlots = new HashMap<>();

        for (var block : mf) {
            for (int i = 0; i < block.size(); ++i) {
                if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why is there a non-machine instruction in a machine function?");
                }

                for (var operand : inst.getOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView rv) {
                        IceMachineRegister reg = rv.getRegister();
                        assert !reg.isVirtualize() : "Virtual registers should not be present in machine instructions after register allocation.";
                        if (isCalleeSaved(reg)) {
                            // 如果是 callee-saved 寄存器，记录下来
                            usedRegs.add(reg);
                            regSlots.putIfAbsent(reg, mf.allocateVariableStackSlot(reg.getType()));
                        }
                    }
                }
            }
        }

        var entryBlock = mf.getEntryBlock();
        var exitBlock = findExitBlock(mf);

        for (var reg : usedRegs) {
            IceMachineInstruction str = new ARM64Instruction("STR {src}, {local:dst}", reg.createView(reg.getType()), regSlots.get(reg));
            IceMachineInstruction ldr = new ARM64Instruction("LDR {src}, {local:dst}", reg.createView(reg.getType()), regSlots.get(reg));
            entryBlock.add(0, str);
            Log.d(exitBlock.getTextIR());
            exitBlock.add(exitBlock.size() - 1, ldr);
        }
    }

    private static IceBlock findExitBlock(IceMachineFunction mf) {
        for (var block : mf) {
            if (block.getSuccessors().isEmpty()) {
                return block; // 找到没有后继的块，作为退出块
            }
        }

        throw new IllegalStateException("No exit block found in the machine function.");
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
