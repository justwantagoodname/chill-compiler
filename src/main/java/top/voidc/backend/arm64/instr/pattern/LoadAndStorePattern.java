package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

import static top.voidc.ir.machine.InstructionSelectUtil.isConstInt;
import static top.voidc.ir.machine.InstructionSelectUtil.isImm16;

public class LoadAndStorePattern {
    public static class LoadRegFuncParam extends InstructionPattern<IceFunction.IceFunctionParameter> {

        /**
         * 匹配函数参数 然后从物理寄存器移动到虚拟寄存器
         */
        // TODO 测试一下后面需要改
        public LoadRegFuncParam() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceFunction.IceFunctionParameter value) {
            final var paramReg = selector.getMachineFunction().getRegisterForValue(value)
                    .orElseThrow(UnsupportedOperationException::new); // TODO: 内存参数的需要load
            return 0;
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceFunction.IceFunctionParameter value) {
            // TODO: 内存参数的需要load
            return selector.getMachineFunction().getRegisterForValue(value)
                    .orElseThrow(UnsupportedOperationException::new);
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceFunction.IceFunctionParameter;
        }
    }

    public static class LoadIntImmediateToReg extends InstructionPattern<IceConstantInt> {

        public LoadIntImmediateToReg() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConstantInt value) {
            if (isImm16(value)) {
                return 2;
            } else {
                return 1;
            }
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceConstantInt value) {
            // TODO：充分利用movz movz
            final var constValue = (int) value.getValue();
            final var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            if (!isImm16(value)) {
                var lowBit = constValue & 0xFFFF;
                var highBit = constValue >> 16;
                selector.addEmittedInstruction(new ARM64Instruction("MOVZ {dst}, {imm16:x}", dstReg, IceConstantData.create(lowBit)));
                selector.addEmittedInstruction(new ARM64Instruction("MOVK {dst}, {imm16:x}, lsl #16", dstReg, IceConstantData.create(highBit)));
                return dstReg;
            } else {
                final var mov = new ARM64Instruction("MOVZ {dst}, {imm16:x}", dstReg, value);
                selector.addEmittedInstruction(mov);
            }
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return isConstInt(value);
        }
    }

    public static class LoadIntZeroToReg extends InstructionPattern<IceConstantInt> {
        public LoadIntZeroToReg() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConstantInt value) {
            return 0;
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceConstantInt value) {
            return selector.getMachineFunction().allocatePhysicalRegister("wzr", IceType.I32);
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConstantInt val && val.equals(IceConstantData.create(0));
        }
    }
}
