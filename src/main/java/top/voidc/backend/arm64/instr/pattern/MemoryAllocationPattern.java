package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.machine.*;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;

/**
 * 内存分配指令模式匹配模块 - 处理alloca指令
 */
public class MemoryAllocationPattern {

    public static class SimpleAllocaPattern extends InstructionPattern<IceAllocaInstruction> {

        public SimpleAllocaPattern() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceAllocaInstruction value) {
            return getIntrinsicCost();
        }

        @Override
        public IceStackSlot emit(InstructionSelector selector, IceAllocaInstruction alloca) {
            // 获取分配的类型
            IceType allocatedType = alloca.getType().getPointTo();

            // 在machine function中创建栈槽
            var slot = selector.getMachineFunction().allocateVariableStackSlot(allocatedType);

            // 设置栈槽的对齐要求
            slot.setAlignment(allocatedType.getByteSize());

            return slot;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配基本类型的alloca
            return value instanceof IceAllocaInstruction alloca && !alloca.getType().getPointTo().isArray();
        }
    }

    public static class ArrayAllocaPattern extends InstructionPattern<IceAllocaInstruction> {

        public ArrayAllocaPattern() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceAllocaInstruction value) {
            return getIntrinsicCost();
        }

        @Override
        public IceStackSlot emit(InstructionSelector selector, IceAllocaInstruction alloca) {
            // 获取数组类型
            IceType allocatedType = alloca.getType().getPointTo();

            // 在machine function中创建栈槽
            IceStackSlot slot = selector.getMachineFunction().allocateVariableStackSlot(allocatedType);

            // 数组需要更高的对齐要求（至少8字节）
            slot.setAlignment(Math.max(allocatedType.getByteSize(), 8));

            return slot;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配数组类型的alloca
            return value instanceof IceAllocaInstruction alloca && alloca.getType().getPointTo().isArray();
        }
    }
}