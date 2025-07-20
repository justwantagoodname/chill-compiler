package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.IceGEPInstruction;
import top.voidc.ir.ice.instruction.IceLoadInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;

import static top.voidc.ir.machine.InstructionSelectUtil.*;

public class MemoryAccessPatterns {

    /**
     * 从栈槽加载值到寄存器
     */
    public static class LoadStackPattern extends InstructionPattern<IceLoadInstruction> {

        public LoadStackPattern() {
            super(10);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceLoadInstruction load) {
            // 获取源指针（应该是栈槽）
            IceValue pointer = load.getSource();
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
            return value instanceof IceLoadInstruction load && canBeStackSlot(selector, load.getSource());
        }
    }

    /**
     * 将值存储到栈槽
     */
    public static class StoreStackPattern extends InstructionPattern<IceStoreInstruction> {

        public StoreStackPattern() {
            super(10);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceStoreInstruction store) {
            // 获取要存储的值
            IceValue valueToStore = store.getTargetPtr();
            IceMachineValue src = selector.emit(valueToStore);

            // 获取目标指针（应该是栈槽）
            IceValue pointer = store.getValue();
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
            return value instanceof IceStoreInstruction store && canBeStackSlot(selector, store.getTargetPtr());
        }
    }

    /**
     * 从寄存器指针加载值
     */
    public static class LoadRegisterPointerPattern extends InstructionPattern<IceLoadInstruction> {

        public LoadRegisterPointerPattern() {
            super(10);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceLoadInstruction load) {
            // 获取源指针（应该在寄存器中）
            IceValue pointer = load.getSource();
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
            return value instanceof IceLoadInstruction load && canBeReg(selector, load.getSource());
        }
    }

    /**
     * 将值存储到寄存器指针
     */
    public static class StoreRegisterPointerPattern extends InstructionPattern<IceStoreInstruction> {

        public StoreRegisterPointerPattern() {
            super(10);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceStoreInstruction store) {
            // 获取目标指针
            IceValue valueToStore = store.getTargetPtr();
            IceMachineValue src = selector.emit(valueToStore);

            // 获取要存储的值
            IceValue pointer = store.getValue();
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
            return value instanceof IceStoreInstruction store && canBeReg(selector, store.getTargetPtr());
        }
    }

    /**
     * 加载全局变量的指针地址
     */
    public static class LoadGlobalPointer extends InstructionPattern<IceGlobalVariable> {
        public LoadGlobalPointer() {
            super(2);
        }

        @Override
        public int getCost(InstructionSelector selector, IceGlobalVariable value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceGlobalVariable variable) {
            // 获取源指针（应该是全局变量）
            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(variable.getType());

            selector.addEmittedInstruction(new ARM64Instruction("ADRP {dst}, " + variable.getName(), dstReg));
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {addr}, :lo12:" + variable.getName(), dstReg, dstReg));
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是全局常量
            return value instanceof IceGlobalVariable;
        }
    }

    /**
     * 加载GEP变成为指针寄存器
     */
    public static class GEPLoadPointer extends InstructionPattern<IceGEPInstruction> {
        public GEPLoadPointer() {
            super(0);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceGEPInstruction gep) {
            // 获取全局变量
            IceGlobalVariable globalVar = (IceGlobalVariable) gep.getBasePtr();
            var basePtr = (IceMachineRegister.RegisterView) selector.emit(globalVar);

            var dstReg = selector.getMachineFunction().allocateVirtualRegister(gep.getType());


            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceGEPInstruction gep && gep.getBasePtr() instanceof IceGlobalVariable;
        }
    }
}