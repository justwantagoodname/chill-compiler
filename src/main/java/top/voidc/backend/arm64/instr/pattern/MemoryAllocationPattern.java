package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.machine.*;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.backend.arm64.instr.ARM64Instruction;

import java.util.List;

/**
 * 内存分配指令模式匹配模块 - 处理alloca指令
 */
public class MemoryAllocationPattern {

    public static class SimpleAllocaPattern extends InstructionPattern<IceAllocaInstruction> {

        public SimpleAllocaPattern() {
            super(1);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceAllocaInstruction alloca) {
            // 获取分配的类型
            IceType allocatedType = alloca.getType().getPointTo();

            // 在machine function中创建栈槽
            IceMachineFunction machineFunction = selector.getMachineFunction();
            IceStackSlot slot = new IceStackSlot(machineFunction, allocatedType);

            // 设置栈槽的对齐要求
            slot.setAlignment(allocatedType.getByteSize());

            return slot;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配基本类型的alloca，并且没有操作数（即不是动态分配）
            return value instanceof IceAllocaInstruction &&
                    ((IceAllocaInstruction) value).getOperands().isEmpty();
        }
    }

    public static class ArrayAllocaPattern extends InstructionPattern<IceAllocaInstruction> {

        public ArrayAllocaPattern() {
            super(2);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceAllocaInstruction alloca) {
            // 获取数组类型
            IceType allocatedType = alloca.getType().getPointTo();

            // 在machine function中创建栈槽
            IceMachineFunction machineFunction = selector.getMachineFunction();
            IceStackSlot slot = new IceStackSlot(machineFunction, allocatedType);

            // 数组需要更高的对齐要求（至少8字节）
            slot.setAlignment(Math.max(allocatedType.getByteSize(), 8));

            return slot;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配数组类型的alloca，并且没有操作数（即不是动态分配）
            return value instanceof IceAllocaInstruction &&
                    ((IceAllocaInstruction) value).getType().getPointTo().isArray() &&
                    ((IceAllocaInstruction) value).getOperands().isEmpty();
        }
    }

    public static class DynamicAllocaPattern extends InstructionPattern<IceAllocaInstruction> {

        public DynamicAllocaPattern() {
            super(3);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceAllocaInstruction alloca) {
            IceMachineFunction mf = selector.getMachineFunction();

            // 1. 获取分配大小
            IceValue sizeValue = alloca.getOperands().get(0);
            IceMachineValue sizeReg = selector.emit(sizeValue);

            // 2. 计算对齐后的大小
            var alignedSizeReg = mf.allocateVirtualRegister(IceType.I64);
            selector.addEmittedInstruction(new ARM64Instruction(
                    "ADD {0}, {1}, 15",
                    alignedSizeReg, sizeReg
            ));
            selector.addEmittedInstruction(new ARM64Instruction(
                    "AND {0}, {1}, -16",
                    alignedSizeReg, alignedSizeReg
            ));

            // 3. 调整栈指针（只生成一条指令）
            selector.addEmittedInstruction(new ARM64Instruction(
                    "SUB sp, sp, {0}",
                    alignedSizeReg
            ));

            // 4. 保存当前栈指针到寄存器
            var addrReg = mf.allocateVirtualRegister(IceType.I64);
            selector.addEmittedInstruction(new ARM64Instruction(
                    "MOV {0}, sp",
                    addrReg
            ));

            return addrReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配有操作数的alloca（动态分配）
            return value instanceof IceAllocaInstruction &&
                    !((IceAllocaInstruction) value).getOperands().isEmpty();
        }
    }
}