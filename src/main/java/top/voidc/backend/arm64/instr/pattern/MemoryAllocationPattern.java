package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantByte;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.IceIntrinsicInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.machine.*;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;

import static top.voidc.ir.machine.InstructionSelectUtil.canBeReg;
import static top.voidc.ir.machine.InstructionSelectUtil.canBeStackSlot;

/**
 * 内存分配指令模式匹配模块 - 处理alloca指令
 */
public class MemoryAllocationPattern {

    // 返回 Slot 对象
    public static class SimpleAlloca extends InstructionPattern<IceAllocaInstruction> {

        public SimpleAlloca() {
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
            slot.setAlignment(Math.max(4, allocatedType.getByteSize())); // 至少4字节对齐

            return slot;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配基本类型的alloca
            return value instanceof IceAllocaInstruction alloca && !alloca.getType().getPointTo().isArray();
        }
    }

    // 返回 Slot 对象
    public static class ArrayAlloca extends InstructionPattern<IceAllocaInstruction> {

        public ArrayAlloca() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceAllocaInstruction value) {
            return getIntrinsicCost();
        }

        @Override
        public IceStackSlot emit(InstructionSelector selector, IceAllocaInstruction alloca) {
            // 获取数组类型
            var allocatedType = (IceArrayType) alloca.getType().getPointTo();

            // 在machine function中创建栈槽
            IceStackSlot slot = selector.getMachineFunction().allocateVariableStackSlot(allocatedType);

            // 数组需要更高的对齐要求（至少4字节）
            slot.setAlignment(Math.max(4, allocatedType.getInsideElementType().getByteSize()));

            return slot;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配数组类型的alloca
            return value instanceof IceAllocaInstruction alloca && alloca.getType().getPointTo().isArray();
        }
    }

    public static class MemsetIntrinsic extends InstructionPattern<IceIntrinsicInstruction> {

        public MemsetIntrinsic() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        // void *memset(void *str, int c, size_t n);
        @Override
        public IceMachineValue emit(InstructionSelector selector, IceIntrinsicInstruction value) {
            // TODO len 小于 8 的直接换成NEON STR wzr

            // 第四位是 volatile 位直接不管了

            // TODO 先想办法保存原来的寄存器值
            var x0 = selector.getMachineFunction().getPhysicalRegister("x0").createView(IceType.I64); // 第一个参数是地址
            var x1 = selector.getMachineFunction().getPhysicalRegister("x1").createView(IceType.I8); // 第二个参数是值
            var x2 = selector.getMachineFunction().getPhysicalRegister("x2").createView(IceType.I32); // 第三个参数是长度
            var slot = (IceStackSlot) selector.emit(value.getParameters().get(0));
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, sp, {local-offset:offset}", x0, slot));
            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {imm8:val}", x1, (IceConstantByte) value.getParameters().get(1))); // 仅仅支持常量其他不管了
            var len = (IceMachineRegister.RegisterView) selector.emit(value.getParameters().get(2));
            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", x2, len));
            selector.addEmittedInstruction(new ARM64Instruction("BL memset"));
            selector.getMachineFunction().setHasCall(true);
            return null;
        }

        @Override
        public int getCost(InstructionSelector selector, IceIntrinsicInstruction value) {
            return getIntrinsicCost();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceIntrinsicInstruction intrinsic
                    && intrinsic.getIntrinsicName().equals(IceIntrinsicInstruction.MEMSET)
                    && canBeStackSlot(selector, intrinsic.getParameters().get(0))
                    && canBeReg(selector, intrinsic.getParameters().get(1))
                    && canBeReg(selector, intrinsic.getParameters().get(2));
        }
    }

    public static class MemcpyIntrinsic extends InstructionPattern<IceIntrinsicInstruction> {

        public MemcpyIntrinsic() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceIntrinsicInstruction value) {
            // TODO len 小于 8 的直接换成STR wzr
            var slot = (IceStackSlot) selector.emit(value.getParameters().getFirst());
            var src = (IceGlobalVariable) value.getParameters().get(1); // 支持常量其他不管了
            // 第四位是 volatile 位直接不管了

            var addrReg = selector.getMachineFunction().allocateVirtualRegister(src.getType());



            // TODO 先想办法保存原来的寄存器值
            var x0 = selector.getMachineFunction().getPhysicalRegister("x0").createView(IceType.I64); // 第一个参数是地址
            var x1 = selector.getMachineFunction().getPhysicalRegister("x1").createView(IceType.I64); // 第二个参数是值
            var x2 = selector.getMachineFunction().getPhysicalRegister("x2").createView(IceType.I32); // 第三个参数是长度

            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, sp, {local-offset:offset}", x0, slot)); // dst

            // TODO 使用已经有的模式
            selector.addEmittedInstruction(new ARM64Instruction("ADRP {dst}, " + src.getName(), addrReg));
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {addr}, :lo12:" + src.getName(), addrReg, addrReg));
            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", x1, addrReg)); // src

            var len = (IceMachineRegister.RegisterView) selector.emit(value.getParameters().get(2));
            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", x2, len));
            selector.addEmittedInstruction(new ARM64Instruction("BL memcpy"));
            selector.getMachineFunction().setHasCall(true);
            return null;
        }

        @Override
        public int getCost(InstructionSelector selector, IceIntrinsicInstruction value) {
            return getIntrinsicCost();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceIntrinsicInstruction intrinsic
                    && intrinsic.getIntrinsicName().equals(IceIntrinsicInstruction.MEMCPY)
                    && canBeStackSlot(selector, intrinsic.getParameters().get(0))
                    && canBeReg(selector, intrinsic.getParameters().get(2));
        }
    }
}