package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Tool;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class RegSaver implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    private static boolean isCallerSaved(IceMachineRegister reg) {
        // ARM64 的只有整形和向量寄存器是
        return Tool.getArm64RegisterType(reg) == Tool.RegisterType.CALLER_SAVED;
    }

    private static boolean isCalleeSaved(IceMachineRegister reg) {
        return Tool.getArm64RegisterType(reg) == Tool.RegisterType.CALLEE_SAVED;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        saveCalleeSavedRegs(target);
        return false; // 不需要修改 IR 只是声明栈槽了
    }

    /**
     * 这个由寄存器分配器保证
     */
    @Deprecated
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

    private void saveCalleeSavedRegs(IceMachineFunction mf) {
        // 直到这次被调用前，使用过的 callee-save 寄存器集合
        Set<IceMachineRegister> usedRegs = new HashSet<>();

        for (var block : mf) {
            for (var iceInstruction : block) {
                assert iceInstruction instanceof IceMachineInstruction : "Why is there a non-machine instruction in a machine function?";

                for (var operand : iceInstruction.getOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView rv) {
                        IceMachineRegister reg = rv.getRegister();
                        assert !reg.isVirtualize() : "Virtual registers should not be present in machine instructions after register allocation.";
                        if (isCalleeSaved(reg)) {
                            // 如果是 callee-saved 寄存器，记录下来
                            usedRegs.add(reg);
                        }
                    }
                }
            }
        }

        usedRegs.forEach(mf::allocateSavedRegisterStackSlot); // 只用分配一个栈槽让 MachineFunction 知道就行了
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
    public int getArchitectureBitSize() {
        return 64;
    }
}
