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
import top.voidc.misc.Log;

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
            Log.d("LoadStackPattern 被激活");
            // 获取源指针（应该是栈槽）
            IceValue pointer = load.getOperands().getFirst();
            IceMachineValue src = selector.emit(pointer);

            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(load.getType());

            // 生成加载指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "LDR {dst}, {local:src}",
                    dstReg, src
            ));

            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是栈槽
            return value instanceof IceLoadInstruction load &&
                    load.getOperands().getFirst() instanceof IceStackSlot;
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
            Log.d("StoreStackPattern 被激活");

            // 获取要存储的值
            IceValue valueToStore = store.getOperands().get(0);
            IceMachineValue src = selector.emit(valueToStore);

            // 获取目标指针（应该是栈槽）
            IceValue pointer = store.getOperands().get(1);
            IceMachineValue dst = selector.emit(pointer);

            // 生成存储指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "STR {src}, {local:dst}",
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
            Log.d("LoadRegisterPointerPattern 被激活");

            // 获取源指针（应该在寄存器中）
            IceValue pointer = load.getOperands().getFirst();
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
                    !(load.getOperands().getFirst() instanceof IceStackSlot);
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
            Log.d("StoreRegisterPointerPattern 被激活");

            // 获取目标指针
            IceValue valueToStore = store.getOperands().get(0);
            IceMachineValue src = selector.emit(valueToStore);

            // 获取要存储的值
            IceValue pointer = store.getOperands().get(1);
            IceMachineValue dst = selector.emit(pointer);

            // 生成存储指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "STR {dst}, [{src}]",
                    dst, src
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
            Log.d("LoadImmediatePattern 被激活");

            // 获取源指针（应该是立即数）
            IceValue pointer = load.getOperands().getFirst();

            // 确保立即数转换为IceMachineValue
            if (!(pointer instanceof IceMachineValue src)) {
                throw new IllegalArgumentException("LoadImmediatePattern requires IceMachineValue operand");
            }

            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(load.getType());

            // 生成加载指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "MOV {0}, {imm16:1}",
                    dstReg, src
            ));

            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是立即数
            return value instanceof IceLoadInstruction load &&
                    load.getOperands().getFirst() instanceof IceConstant;
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
            Log.d("StoreImmediatePattern 被激活");

            // 获取要存储的值（立即数）
            IceValue valueToStore = store.getOperands().get(0);

            // 确保立即数转换为IceMachineValue
            if (!(valueToStore instanceof IceMachineValue src)) {
                throw new IllegalArgumentException("StoreImmediatePattern requires IceMachineValue operand");
            }

            // 获取目标指针0
            IceValue pointer = store.getOperands().get(1);
            IceMachineValue dst = selector.emit(pointer);

            // 根据目标类型选择模板
            String template = (dst instanceof IceMachineRegister) ?
                    "STR {imm16:0}, [{1}]" : "STR {imm16:0}, {1}";

            // 生成存储指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    template,
                    src, dst
            ));

            return null; // 存储指令无返回值
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配store指令，且源操作数是立即数
            return value instanceof IceStoreInstruction store &&
                    store.getOperands().getFirst() instanceof IceConstant;
        }
    }
}