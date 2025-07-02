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
            var addInstr = (IceBinaryInstruction.Add) value;
            var xReg = selector.emit(addInstr.getLhs());
            var yReg = selector.emit(addInstr.getRhs());
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

    public static class MULTwoReg extends InstructionPattern {

        public MULTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            // x * y -> dst
            var mulInstr = (IceBinaryInstruction.Mul) value;
            var xReg = selector.emit(mulInstr.getLhs());
            var yReg = selector.emit(mulInstr.getRhs());
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

    public static class MADDInstruction extends InstructionPattern {

        public MADDInstruction() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceValue value) {
            var cost = 1;
            var addInstr = (IceBinaryInstruction.Add) value;
            if (addInstr.getLhs() instanceof IceBinaryInstruction.Mul mulNode) {
                cost += selector.select(addInstr.getRhs()).cost();
                cost += selector.select(mulNode.getLhs()).cost();
                cost += selector.select(mulNode.getRhs()).cost();
            } else {
                var mulNode = (IceBinaryInstruction.Mul) addInstr.getRhs();
                cost += selector.select(addInstr.getLhs()).cost();
                cost += selector.select(mulNode.getLhs()).cost();
                cost += selector.select(mulNode.getRhs()).cost();
            }
            return cost;
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            // x * y + z -> dst
            var addInstr = (IceBinaryInstruction.Add) value;
            IceMachineRegister xReg, yReg, zReg;
            if (addInstr.getLhs() instanceof IceBinaryInstruction.Mul mulNode) {
                zReg = selector.emit(addInstr.getRhs());
                xReg = selector.emit(mulNode.getLhs());
                yReg = selector.emit(mulNode.getRhs());
            } else {
                var mulNode = (IceBinaryInstruction.Mul) addInstr.getRhs();
                zReg = selector.emit(addInstr.getLhs());
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
                return (addNode.getLhs() instanceof IceBinaryInstruction.Mul && isReg(addNode.getRhs()))
                        || (addNode.getRhs() instanceof IceBinaryInstruction.Mul && isReg(addNode.getLhs()));
            }
            return false;
        }
    }
}
