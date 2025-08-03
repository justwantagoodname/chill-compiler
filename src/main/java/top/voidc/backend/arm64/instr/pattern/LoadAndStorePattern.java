package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.instruction.IceCopyInstruction;
import top.voidc.ir.ice.instruction.IcePHINode;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.misc.Tool;

import static top.voidc.ir.machine.InstructionSelectUtil.*;

public class LoadAndStorePattern {
    public static class LoadRegFuncParam extends InstructionPattern<IceFunction.IceFunctionParameter> {
        /**
         * 匹配函数参数 然后从物理寄存器移动到虚拟寄存器
         */
        public LoadRegFuncParam() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceFunction.IceFunctionParameter value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceFunction.IceFunctionParameter value) {
            return (IceMachineRegister.RegisterView) selector.getRegisterForValue(value)
                    .orElseThrow(UnsupportedOperationException::new);
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceFunction.IceFunctionParameter;
        }
    }

    public static class LoadIntImmediateToReg extends InstructionPattern<IceConstantData> {

        public LoadIntImmediateToReg() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConstantData value) {
            if (isImm16(value)) {
                return 2;
            } else {
                return 1;
            }
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConstantData value) {
            // TODO：充分利用movz movz
            final var constValue = ((IceConstantInt) value.castTo(IceType.I32)).getValue();
            final var dstRegView = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            if (!isImm16(value)) {
                var lowBit = constValue & 0xFFFF;
                var highBit = constValue >> 16;
                selector.addEmittedInstruction(new ARM64Instruction("MOVZ {dst}, {imm16:x}", dstRegView, IceConstantData.create(lowBit)));
                selector.addEmittedInstruction(new ARM64Instruction("MOVK {dst}, {imm16:x}, lsl #16", dstRegView, IceConstantData.create(highBit)));
                return dstRegView;
            } else {
                final var mov = new ARM64Instruction("MOVZ {dst}, {imm16:x}", dstRegView, value);
                selector.addEmittedInstruction(mov);
            }
            return dstRegView;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConstantData
                    && value.getType().isInteger()
                    && !value.getType().equals(IceType.I64);
        }
    }

    public static class LoadLongImmediateToReg extends InstructionPattern<IceConstantData> {

        public LoadLongImmediateToReg() {
            super(3);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConstantData value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConstantData value) {
            final var constValue = ((IceConstantLong) value.castTo(IceType.I64)).getValue();
            final var dstRegView = selector.getMachineFunction().allocateVirtualRegister(IceType.I64);

            for (int i = 0; i < 4; i++) {
                int part = Math.toIntExact((constValue >> (i * 16)) & 0xFFFF);
                if (part != 0 || i == 0) {
                    if (i == 0) {
                        selector.addEmittedInstruction(new ARM64Instruction("MOVZ {dst}, {imm16:x}", dstRegView, IceConstantData.create(part)));
                    } else {
                        selector.addEmittedInstruction(new ARM64Instruction("MOVK {dst}, {imm16:x}, lsl #" + 16 * i, dstRegView, IceConstantData.create(part)));
                    }
                }
            }
            return dstRegView;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConstantData
                    && value.getType().equals(IceType.I64);
        }
    }

    public static class LoadFloatImmediateToReg extends InstructionPattern<IceConstantFloat> {

        public LoadFloatImmediateToReg() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConstantFloat value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConstantFloat value) {
            final var floatValue = value.getValue();
            final var dstRegView = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            if (Tool.isArm64FloatImmediate(floatValue)) {
                if (floatValue == 0.0f) {
                    var dView = dstRegView.getRegister().createView(IceType.F64);
                    selector.addEmittedInstruction(new ARM64Instruction("MOVI {dst}, #0", dView));
                } else {
                    selector.addEmittedInstruction(new ARM64Instruction("FMOV {dst}, {fimm:f}", dstRegView, value));
                }
            } else {
                var intFloat = IceConstantData.create(Float.floatToIntBits(floatValue));
                selector.select(intFloat);
                var intRegView = (IceMachineRegister.RegisterView) selector.emit(intFloat);
                selector.addEmittedInstruction(new ARM64Instruction("FMOV {dst}, {src}", dstRegView, intRegView));
            }

            return dstRegView;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConstantFloat;
        }
    }

    public static class LoadDoubleImmediateToReg extends InstructionPattern<IceConstantDouble> {

        public LoadDoubleImmediateToReg() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConstantDouble value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConstantDouble value) {
            final var floatValue = value.getValue();
            final var dstRegView = selector.getMachineFunction().allocateVirtualRegister(IceType.F64);

            var longDouble = IceConstantData.create(Double.doubleToRawLongBits(floatValue));
            selector.select(longDouble);
            var intRegView = (IceMachineRegister.RegisterView) selector.emit(longDouble);
            selector.addEmittedInstruction(new ARM64Instruction("FMOV {dst}, {src}", dstRegView, intRegView));

            return dstRegView;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConstantDouble;
        }
    }

    public static class LoadZeroToReg extends InstructionPattern<IceConstantData> {
        public LoadZeroToReg() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConstantData value) {
            return 0;
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConstantData value) {
            return selector.getMachineFunction().getZeroRegister(value.getType());
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return (value instanceof IceConstantData) && value.equals(IceConstantData.create(0));
        }
    }

    public static class IntCopyInst extends InstructionPattern<IceCopyInstruction> {
        public IntCopyInst() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCopyInstruction value) {
            var srcReg = selector.emit(value.getSource());
            assert value.getDestination() instanceof IcePHINode;// 一般目标是PHINode
            // 目标寄存器一般是PHINode，为了防止没有被选择过，先选择一下
            if (selector.select(value.getDestination()) == null) {
                throw new IllegalStateException("phi指令应该可以被选择");
            }
            var dstReg = selector.emit(value.getDestination());
            return selector.addEmittedInstruction(
                    new ARM64Instruction("MOV {dst}, {src}", dstReg, srcReg)).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCopyInstruction copy && copy.getDestination().getType().isInteger() && canBeReg(selector, copy.getSource());
        }
    }

    public static class FloatCopyInst extends InstructionPattern<IceCopyInstruction> {
        public FloatCopyInst() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCopyInstruction value) {
            var srcReg = selector.emit(value.getSource());
            assert value.getDestination() instanceof IcePHINode;// 一般目标是PHINode
            // 目标寄存器一般是PHINode，为了防止没有被选择过，先选择一下
            if (selector.select(value.getDestination()) == null) {
                throw new IllegalStateException("phi指令应该可以被选择");
            }
            var dstReg = selector.emit(value.getDestination());
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FMOV {dst}, {src}", dstReg, srcReg)).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCopyInstruction copy && copy.getDestination().getType().isFloat() && canBeReg(selector, copy.getSource());
        }
    }

    public static class FloatCopyImm extends InstructionPattern<IceCopyInstruction> {
        public FloatCopyImm() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceCopyInstruction value) {
            return getIntrinsicCost() + selector.select(value.getDestination()).cost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCopyInstruction value) {
            assert value.getDestination() instanceof IcePHINode;// 一般目标是PHINode
            // 目标寄存器一般是PHINode，为了防止没有被选择过，先选择一下
            if (selector.select(value.getDestination()) == null) {
                throw new IllegalStateException("phi指令应该可以被选择");
            }
            var dstReg =(IceMachineRegister.RegisterView) selector.emit(value.getDestination());
            var imm = (IceConstantFloat) value.getSource();
            final var floatValue = imm.getValue();

            if (Tool.isArm64FloatImmediate(floatValue)) {
                if (floatValue == 0.0f) {
                    var dView = dstReg.getRegister().createView(IceType.F64);
                    selector.addEmittedInstruction(new ARM64Instruction("MOVI {dst}, #0", dView));
                } else {
                    selector.addEmittedInstruction(new ARM64Instruction("FMOV {dst}, {fimm:f}", dstReg, imm));
                }
            } else {
                var intFloat = IceConstantInt.create(Float.floatToIntBits(floatValue));
                selector.select(intFloat);
                var intRegView = (IceMachineRegister.RegisterView) selector.emit(intFloat);
                selector.addEmittedInstruction(new ARM64Instruction("FMOV {dst}, {src}", dstReg, intRegView));
            }
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCopyInstruction copy
                    && copy.getDestination().getType().isFloat()
                    && copy.getSource() instanceof IceConstantFloat;
        }
    }

    public static class CopyImm extends InstructionPattern<IceCopyInstruction> {
        public CopyImm() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceCopyInstruction value) {
            return getIntrinsicCost() + selector.select(value.getDestination()).cost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCopyInstruction value) {
            assert value.getDestination() instanceof IcePHINode;// 一般目标是PHINode
            // 目标寄存器一般是PHINode，为了防止没有被选择过，先选择一下
            if (selector.select(value.getDestination()) == null) {
                throw new IllegalStateException("phi指令应该可以被选择");
            }
            var dstReg = selector.emit(value.getDestination());
            var imm = (IceConstantInt) ((IceConstantData) value.getSource()).castTo(IceType.I32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("MOV {dst}, {imm12:src}", dstReg, imm)).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCopyInstruction copy 
                    && copy.getDestination().getType().isInteger()
                    && isImm12(copy.getSource())
                    && !(copy.getSource().equals(IceConstantData.create(0)));
        }
    }

    /**
     * PHI指令只是一个占位符，它不生成任何实际的指令仅仅分配一个虚拟寄存器
     */
    public static class DummyPHI extends InstructionPattern<IcePHINode> {

        public DummyPHI() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IcePHINode value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IcePHINode value) {
            // 什么指令也不生成就是生成一个虚拟寄存器
            return selector.getMachineFunction().allocateVirtualRegister(value.getType());
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IcePHINode phiNode && phiNode.isEliminated();
        }
    }
}
