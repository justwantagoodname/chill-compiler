package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.instruction.IceLoadInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;

public class MemoryAccessPatterns {

    /**
     * 从栈槽加载值到寄存器
     */
    public static class LoadStackPattern extends InstructionPattern<IceLoadInstruction> {

        public LoadStackPattern() {
            super(1);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceLoadInstruction load) {
            // 获取源指针（应该是栈槽）
            IceValue pointer = load.getOperands().get(0);
            IceMachineValue src = selector.emit(pointer);

            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(load.getType());

            // 生成加载指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "LDR {dst}, {src}",
                    dstReg, src
            ));

            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是栈槽
            return value instanceof IceLoadInstruction load &&
                    load.getOperands().get(0) instanceof IceStackSlot;
        }
    }

    /**
     * 将值存储到栈槽
     */
    public static class StoreStackPattern extends InstructionPattern<IceStoreInstruction> {

        public StoreStackPattern() {
            super(1);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceStoreInstruction store) {
            // 获取要存储的值
            IceValue valueToStore = store.getOperands().get(0);
            IceMachineValue src = selector.emit(valueToStore);

            // 获取目标指针（应该是栈槽）
            IceValue pointer = store.getOperands().get(1);
            IceMachineValue dst = selector.emit(pointer);

            // 生成存储指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "STR {src}, {dst}",
                    src, dst
            ));

            return null; // 存储指令无返回值
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配store指令，且目标操作数是栈槽
            return value instanceof IceStoreInstruction store &&
                    store.getOperands().get(1) instanceof IceStackSlot;
        }
    }

    /**
     * 从寄存器指针加载值
     */
    public static class LoadRegisterPointerPattern extends InstructionPattern<IceLoadInstruction> {

        public LoadRegisterPointerPattern() {
            super(1);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceLoadInstruction load) {
            // 获取源指针（应该在寄存器中）
            IceValue pointer = load.getOperands().get(0);
            IceMachineValue src = selector.emit(pointer);

            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(load.getType());

            // 生成加载指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "LDR {dst}, [{src}]",
                    dstReg, src
            ));

            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是寄存器指针
            return value instanceof IceLoadInstruction load &&
                    !(load.getOperands().get(0) instanceof IceStackSlot);
        }
    }

    /**
     * 将值存储到寄存器指针
     */
    public static class StoreRegisterPointerPattern extends InstructionPattern<IceStoreInstruction> {

        public StoreRegisterPointerPattern() {
            super(1);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceStoreInstruction store) {
            // 获取要存储的值
            IceValue valueToStore = store.getOperands().get(0);
            IceMachineValue src = selector.emit(valueToStore);

            // 获取目标指针（应该在寄存器中）
            IceValue pointer = store.getOperands().get(1);
            IceMachineValue dst = selector.emit(pointer);

            // 生成存储指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "STR {src}, [{dst}]",
                    src, dst
            ));

            return null; // 存储指令无返回值
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配store指令，且目标操作数是寄存器指针
            return value instanceof IceStoreInstruction store &&
                    !(store.getOperands().get(1) instanceof IceStackSlot);
        }
    }

    /**
     * 加载立即数到寄存器
     */
    public static class LoadImmediatePattern extends InstructionPattern<IceLoadInstruction> {

        public LoadImmediatePattern() {
            super(1);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceLoadInstruction load) {
            // 获取源指针（应该是立即数）
            IceValue pointer = load.getOperands().get(0);

            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(load.getType());

            // 生成加载指令 TODO
//            selector.addEmittedInstruction(new ARM64Instruction(
//                    "MOV {dst}, {src}",
//                    dstReg, pointer
//            ));

            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是立即数
            return value instanceof IceLoadInstruction load &&
                    load.getOperands().get(0) instanceof IceConstant;
        }
    }

    /**
     * 存储立即数到内存
     */
    public static class StoreImmediatePattern extends InstructionPattern<IceStoreInstruction> {

        public StoreImmediatePattern() {
            super(1);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceStoreInstruction store) {
            // 获取要存储的值（立即数）
            IceValue valueToStore = store.getOperands().get(0);

            // 获取目标指针
            IceValue pointer = store.getOperands().get(1);
            IceMachineValue dst = selector.emit(pointer);

            // 如果目标在寄存器中，使用不同的指令格式
            String template = (dst instanceof IceMachineRegister) ?
                    "STR {src}, [{dst}]" : "STR {src}, {dst}";

            // 生成存储指令 TODO
//            selector.addEmittedInstruction(new ARM64Instruction(
//                    template,
//                    valueToStore, dst
//            ));

            return null; // 存储指令无返回值
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配store指令，且源操作数是立即数
            return value instanceof IceStoreInstruction store &&
                    store.getOperands().get(0) instanceof IceConstant;
        }
    }
}