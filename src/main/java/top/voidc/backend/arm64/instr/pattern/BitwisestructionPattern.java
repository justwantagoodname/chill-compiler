package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

import static top.voidc.ir.machine.InstructionSelectUtil.canBeReg;

public class BitwisestructionPattern {
    /**
     * 逻辑左移模式：`x << y -> dst`
     * 支持寄存器移位和立即数移位
     */
    public static class LSLInstruction extends InstructionPattern<IceBinaryInstruction.Shl> {

        public LSLInstruction() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Shl value) {
            var xReg = selector.emit(value.getLhs());
            var yVal = value.getRhs();

            if (isShiftImm(yVal)) {
                // 立即数移位
                var imm = (IceConstantInt) yVal;
                var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
                var inst = new ARM64Instruction("LSL {dst}, {x}, {shift:y}", dstReg, xReg, imm);
                return selector.addEmittedInstruction(inst).getResultReg();
            } else {
                // 寄存器移位
                var yReg = selector.emit(yVal);
                var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
                var inst = new ARM64Instruction("LSL {dst}, {x}, {y}", dstReg, xReg, yReg);
                return selector.addEmittedInstruction(inst).getResultReg();
            }
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Shl shlNode
                    && canBeReg(selector, shlNode.getLhs())
                    && (canBeReg(selector, shlNode.getRhs()) || isShiftImm(shlNode.getRhs()));
        }

        private boolean isShiftImm(IceValue val) {
            return val instanceof IceConstantInt imm
                    && imm.getValue() >= 0
                    && imm.getValue() <= 63;
        }
    }

    /**
     * 算术右移模式：`x >> y -> dst` 
     */
    public static class ASRInstruction extends InstructionPattern<IceBinaryInstruction.Shr> {

        public ASRInstruction() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Shr value) {
            var xReg = selector.emit(value.getLhs());
            var yVal = value.getRhs();

            if (isShiftImm(yVal)) {
                var imm = (IceConstantInt) yVal;
                var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
                var inst = new ARM64Instruction("ASR {dst}, {x}, {shift:y}", dstReg, xReg, imm);
                return selector.addEmittedInstruction(inst).getResultReg();
            } else {
                var yReg = selector.emit(yVal);
                var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
                var inst = new ARM64Instruction("ASR {dst}, {x}, {y}", dstReg, xReg, yReg);
                return selector.addEmittedInstruction(inst).getResultReg();
            }
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Shr shrNode
                    && canBeReg(selector, shrNode.getLhs())
                    && (canBeReg(selector, shrNode.getRhs()) || isShiftImm(shrNode.getRhs()));
        }

        private boolean isShiftImm(IceValue val) {
            return val instanceof IceConstantInt imm
                    && imm.getValue() >= 0
                    && imm.getValue() <= 63;
        }
    }

    /**
     * 位与操作模式：`x & y -> dst`
     */
    public static class ANDInstruction extends InstructionPattern<IceBinaryInstruction.And> {

        public ANDInstruction() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.And value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("AND {dst}, {x}, {y}", dstReg, xReg, yReg);
            return selector.addEmittedInstruction(inst).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.And andNode
                    && canBeReg(selector, andNode.getLhs())
                    && canBeReg(selector, andNode.getRhs());
        }
    }

    /**
     * 位或操作模式：`x | y -> dst`
     */
    public static class ORRInstruction extends InstructionPattern<IceBinaryInstruction.Or> {

        public ORRInstruction() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Or value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("ORR {dst}, {x}, {y}", dstReg, xReg, yReg);
            return selector.addEmittedInstruction(inst).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Or orNode
                    && canBeReg(selector, orNode.getLhs())
                    && canBeReg(selector, orNode.getRhs());
        }
    }

    /**
     * 位异或操作模式：`x ^ y -> dst`
     */
    public static class EORInstruction extends InstructionPattern<IceBinaryInstruction.Xor> {

        public EORInstruction() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Xor value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("EOR {dst}, {x}, {y}", dstReg, xReg, yReg);
            return selector.addEmittedInstruction(inst).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Xor xorNode
                    && canBeReg(selector, xorNode.getLhs())
                    && canBeReg(selector, xorNode.getRhs());
        }
    }
}
