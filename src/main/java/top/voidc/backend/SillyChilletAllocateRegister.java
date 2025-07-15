package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.*;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

/**
 * 笨蛋疾旋鼬寄存器分配器
 * 蠢鼬脑容量不够只能给所有变量全部溢出到栈上
 */
@Pass(group = {"O0", "backend"})
public class SillyChilletAllocateRegister implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    @Override
    public boolean run(IceMachineFunction target) {
        var changed = false;
        // Phase 1: 分配栈帧
        final var slotMap = new HashMap<IceMachineRegister, IceStackSlot>();
        for (var block : target) {
            for (var iceInstruction : block) {
                var instruction = (IceMachineInstruction) iceInstruction;
                // 将所有的虚拟寄存器都分配到栈上
                for (var operand : instruction.getOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView registerView) {
                        if (registerView.getRegister().isVirtualize()) {
                            slotMap.computeIfAbsent(registerView.getRegister(), register ->
                                    target.allocateStackSlot(register.getType(), IceStackSlot.StackSlotType.VARIABLE));
                        }
                    }
                }
            }
        }

        // Phase 2: 重写指令
        final var regPool = List.of(
                target.getPhysicalRegister("x9"),
                target.getPhysicalRegister("x10"),
                target.getPhysicalRegister("x11"),
                target.getPhysicalRegister("x12"),
                target.getPhysicalRegister("x13"),
                target.getPhysicalRegister("x14"),
                target.getPhysicalRegister("x15")
        );
        for (var block : target) {
            for (var i = 0; i < block.size(); i++) {
                var instruction = (IceMachineInstruction) block.get(i);
                var phyRegisterIndex = 0;

                // 处理源操作数
                var sourceOperands = new ArrayList<IceMachineInstruction>();
                for (var operand : List.copyOf(instruction.getSourceOperands())) {
                    if (operand instanceof IceMachineRegister.RegisterView registerView && registerView.getRegister().isVirtualize()) {
                        // 如果是虚拟寄存器，先加载到临时寄存器中
                        var slot = slotMap.get(registerView.getRegister());
                        // 生成ldr指令
                        var load = new ARM64Instruction("LDR {dst}, {local:src}",
                                regPool.get(phyRegisterIndex++).createView(registerView.getType()), slot);
                        sourceOperands.add(load);
                        instruction.replaceOperand(operand, load.getResultReg()); // 替换原指令的虚拟寄存器操作数为实际的寄存器
                    }
                }

                if (!sourceOperands.isEmpty()) {
                    changed = true;
                    sourceOperands.forEach(instr -> instr.setParent(block));
                    block.addAll(i, sourceOperands); // 在原指令前插入加载指令
                    i += sourceOperands.size();
                }


                // 处理目标操作数
                var dstReg = instruction.getResultReg();
                if (dstReg != null) {
                    // 如果是虚拟寄存器，放回栈上
                    if (dstReg.getRegister().isVirtualize()) {
                        changed = true;
                        var phyReg = regPool.get(phyRegisterIndex);
                        var phyView = phyReg.createView(dstReg.getType());
                        instruction.replaceOperand(dstReg, phyView);

                        var slot = slotMap.get(dstReg.getRegister());
                        // 生成str指令
                        var store = new ARM64Instruction("STR {src}, {local:target}", phyView, slot);
                        store.setParent(block);
                        block.add(i + 1, store); // 在原指令后插入存储指令
                        i++; // 跳过存储指令
                    }
                }
            }
        }


        return changed;
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
