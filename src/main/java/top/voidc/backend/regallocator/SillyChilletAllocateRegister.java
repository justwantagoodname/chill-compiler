package top.voidc.backend.regallocator;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.*;
import top.voidc.misc.Flag;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

/**
 * 笨蛋疾旋鼬寄存器分配器
 * 蠢鼬脑容量不够只能给所有变量全部溢出到栈上
 */
@Pass(group = {"O0", "backend"}, parallel = true)
public class SillyChilletAllocateRegister implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    private static class RegisterPool {
        private final List<IceMachineRegister> pool;
        private int allocatedCount = 0;

        public RegisterPool(List<IceMachineRegister> registers) {
            this.pool = registers;
        }

        public IceMachineRegister get() {
            if (allocatedCount == pool.size()) {
                throw new IllegalStateException("No more registers available in the pool");
            }
            return pool.get(allocatedCount++);
        }

        public void release() {
            if (allocatedCount == 0) {
                throw new IllegalStateException("No registers to release");
            }
            allocatedCount--;
        }

        public void releaseAll() {
            allocatedCount = 0;
        }
    }

    @Override
    public boolean run(IceMachineFunction target) {
        var showDebug = Boolean.TRUE.equals(Flag.get("-fshow-trace-info"));
        var changed = false;
        final var intRegPool = new RegisterPool(List.of(
                target.getPhysicalRegister("x19"),
                target.getPhysicalRegister("x20"),
                target.getPhysicalRegister("x21"),
                target.getPhysicalRegister("x22"),
                target.getPhysicalRegister("x23"),
                target.getPhysicalRegister("x24"),
                target.getPhysicalRegister("x25"),
                target.getPhysicalRegister("x26"),
                target.getPhysicalRegister("x27")
        ));
        final var floatRegPool = new RegisterPool(List.of(
                target.getPhysicalRegister("v8"),
                target.getPhysicalRegister("v9"),
                target.getPhysicalRegister("v10"),
                target.getPhysicalRegister("v11"),
                target.getPhysicalRegister("v12"),
                target.getPhysicalRegister("v13"),
                target.getPhysicalRegister("v14"),
                target.getPhysicalRegister("v15")
        ));

        // Phase 1: 分配栈帧
        final var slotMap = new HashMap<IceMachineRegister, IceStackSlot>();
        for (var block : target) {
            for (var iceInstruction : block) {
                var instruction = (IceMachineInstruction) iceInstruction;
                // 将所有的虚拟寄存器都分配到栈上
                for (var operand : instruction.getOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView registerView) {
                        if (registerView.getRegister().isVirtualize()) {
                            // Note: 按照虚拟寄存器来分配栈槽而不是其寄存器视图，但是访问的时候是按照视图大小访问
                            var slot = slotMap.computeIfAbsent(registerView.getRegister(), register ->
                                    target.allocateVariableStackSlot(register.getType()));
                            var alignment = switch (registerView.getRegister().getType().getTypeEnum()) {
                                case I32, F32 -> 4;
                                case I64, F64, PTR -> 8;
                                default -> throw new IllegalArgumentException("Unsupported type: " + registerView.getRegister().getType());
                            };
                            slot.setAlignment(alignment);
                        }
                    }
                }
            }
        }

        // Phase 2: 重写指令
        for (var block : target) {
            for (var i = 0; i < block.size(); i++) {
                var instruction = (IceMachineInstruction) block.get(i);

                var loadSourceOperandsInstructions = new ArrayList<IceMachineInstruction>();
                var dstReg = instruction.getResultReg(); // 一定要在插入加载指令前获取目标寄存器因为有可能在和源操作数重合的情况下被覆盖
                IceMachineRegister dstPhyReg = null;

                // 处理源操作数

                for (var operand : List.copyOf(instruction.getSourceOperands())) {
                    if (operand instanceof IceMachineRegister.RegisterView registerView && registerView.getRegister().isVirtualize()) {
                        // 如果是虚拟寄存器，先加载到临时寄存器中
                        var slot = slotMap.get(registerView.getRegister());

                        var pool = registerView.getType().isFloat() ? floatRegPool : intRegPool; // 根据虚拟寄存器类型选择寄存器池
                        var phyRegister = pool.get(); // 获取一个物理寄存器

                        // 生成ldr指令
                        var load = new ARM64Instruction("LDR {dst}, {local:src}" + (showDebug ? " //" + registerView.getRegister().getReferenceName() : ""),
                                phyRegister.createView(registerView.getType()), slot);
                        loadSourceOperandsInstructions.add(load);
                        instruction.replaceOperand(operand, load.getResultReg().getRegister().createView(registerView.getType())); // 替换原指令的虚拟寄存器操作数为实际的寄存器

                        if (dstReg != null && dstReg.getRegister().equals(registerView.getRegister())) {
                            // 如果目标寄存器也是这个虚拟寄存器，记录下来 以便分配同一个物理寄存器
                            dstPhyReg = phyRegister;
                        }
                    }
                }

                if (!loadSourceOperandsInstructions.isEmpty()) {
                    changed = true;
                    loadSourceOperandsInstructions.forEach(instr -> instr.setParent(block));
                    block.addAll(i, loadSourceOperandsInstructions); // 在原指令前插入加载指令
                    i += loadSourceOperandsInstructions.size(); // 更新索引，跳过插入的加载指令
                }

                // 处理目标操作数
                if (dstReg != null) {
                    // 如果是虚拟寄存器，放回栈上
                    if (dstReg.getRegister().isVirtualize()) {
                        changed = true;
                        var pool = dstReg.getType().isFloat() ? floatRegPool : intRegPool; // 根据虚拟寄存器类型选择寄存器池
                        var phyReg = dstPhyReg == null ? pool.get() : dstPhyReg; // 如果没有记录到物理寄存器，则使用下一个物理寄存器
                        var slot = slotMap.get(dstReg.getRegister());
                        var phyView = phyReg.createView(dstReg.getType());
                        instruction.replaceOperand(dstReg, phyReg.createView(dstReg.getType()));
                        // 生成str指令
                        var store = new ARM64Instruction("STR {src}, {local:target}" + (showDebug ? " //" + dstReg.getRegister().getReferenceName() : ""),
                                phyView, slot);
                        store.setParent(block);
                        block.add(i + 1, store); // 在原指令后插入存储指令
                        i++; // 跳过存储指令
                    }
                }

                intRegPool.releaseAll();
                floatRegPool.releaseAll();
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
    public int getArchitectureBitSize() {
        return 64;
    }
}
