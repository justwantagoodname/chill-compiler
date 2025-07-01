package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import static top.voidc.ir.machine.InstructionSelectUtil.isReg;

public class ArithmaticInstructionPattern {

    public static class ADDTwoReg extends InstructionPattern {

        public ADDTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            // x + y = dst
            var inst = new ARM64Instruction("ADD {dst}, {x}, {y}"); // TODO: add operand
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Add addNode
                    && isReg(addNode.getLhs())
                    && isReg(addNode.getRhs());
        }

    }

    public static class MULTwoReg extends InstructionPattern {

        public MULTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            // x * y -> dst
            var inst = new ARM64Instruction("MUL dst, x, y");
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Mul mulNode
                    && isReg(mulNode.getLhs())
                    && isReg(mulNode.getRhs());
        }
    }

    public static class MADDInstruction extends InstructionPattern {

        public MADDInstruction() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            // x * y + z -> dst
            var inst = new ARM64Instruction("MADD dst, x, y, z");
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (value instanceof IceBinaryInstruction.Add addNode) {
                return (addNode.getLhs() instanceof IceBinaryInstruction.Mul && addNode.getRhs() instanceof IceInstruction)
                        || (addNode.getRhs() instanceof IceBinaryInstruction.Mul && addNode.getLhs() instanceof IceInstruction);
            }
            return false;
        }
    }
}
