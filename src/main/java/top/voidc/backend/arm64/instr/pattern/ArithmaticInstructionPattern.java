package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

import static top.voidc.ir.machine.InstructionSelectUtil.commutativePredicate;
import static top.voidc.ir.machine.InstructionSelectUtil.isReg;

public class ArithmaticInstructionPattern {

    public static class ADDTwoReg extends InstructionPattern<IceBinaryInstruction.Add> {

        public ADDTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Add value) {
            // x + y = dst
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("ADD {dst}, {x}, {y}", dstReg, xReg, yReg);
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

    public static class MULTwoReg extends InstructionPattern<IceBinaryInstruction.Mul> {

        public MULTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Mul value) {
            // x * y -> dst
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("MUL {dst}, {x}, {y}", dstReg, xReg, yReg);
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

    public static class MADDInstruction extends InstructionPattern<IceBinaryInstruction.Add> {

        public MADDInstruction() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.Add value) {
            var cost = 1;
            if (value.getLhs() instanceof IceBinaryInstruction.Mul mulNode) {
                cost += selector.select(value.getRhs()).cost();
                cost += selector.select(mulNode.getLhs()).cost();
                cost += selector.select(mulNode.getRhs()).cost();
            } else {
                var mulNode = (IceBinaryInstruction.Mul) value.getRhs();
                cost += selector.select(value.getLhs()).cost();
                cost += selector.select(mulNode.getLhs()).cost();
                cost += selector.select(mulNode.getRhs()).cost();
            }
            return cost;
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Add value) {
            // x * y + z -> dst
            IceMachineRegister xReg, yReg, zReg;
            if (value.getLhs() instanceof IceBinaryInstruction.Mul mulNode) {
                zReg = selector.emit(value.getRhs());
                xReg = selector.emit(mulNode.getLhs());
                yReg = selector.emit(mulNode.getRhs());
            } else {
                var mulNode = (IceBinaryInstruction.Mul) value.getRhs();
                zReg = selector.emit(value.getLhs());
                xReg = selector.emit(mulNode.getLhs());
                yReg = selector.emit(mulNode.getRhs());
            }
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("MADD {dst}, {x}, {y}, {z}", dstReg, xReg, yReg, zReg);
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (value instanceof IceBinaryInstruction.Add addNode) {
                return commutativePredicate(addNode,
                        (lhs, rhs) -> lhs instanceof IceBinaryInstruction.Mul && isReg(rhs));
            }
            return false;
        }
    }
}
