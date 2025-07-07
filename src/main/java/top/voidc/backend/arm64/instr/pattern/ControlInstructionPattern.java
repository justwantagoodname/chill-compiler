package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

public class ControlInstructionPattern {
    public static class RetVoid extends InstructionPattern<IceRetInstruction> {

        public RetVoid() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceRetInstruction value) {
            selector.addEmittedInstruction(new ARM64Instruction("RET"));
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceRetInstruction ret && ret.isReturnVoid();
        }
    }

    public static class RetInteger extends InstructionPattern<IceRetInstruction> {

        public RetInteger() {
            super(2);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceRetInstruction value) {
            var resultReg = selector.emit(value.getReturnValue().orElseThrow());
            selector.addEmittedInstruction(
                    new ARM64Instruction("MOV {dst}, {x}", selector.getMachineFunction().getReturnRegister(IceType.I32), resultReg));
            selector.addEmittedInstruction(new ARM64Instruction("RET"));
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceRetInstruction ret
                    && ret.getReturnValue().isPresent()
                    && ret.getReturnValue().get().getType().isInteger();
        }
    }

    public static class BranchUnconditional extends InstructionPattern<IceBranchInstruction> {
        public BranchUnconditional() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBranchInstruction value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBranchInstruction value) {
            var targetBlock = selector.getMachineFunction().getMachineBlock(value.getTargetBlock().getName());
            selector.addEmittedInstruction(new ARM64Instruction("B {label:target}", targetBlock));
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBranchInstruction branch && !branch.isConditional();
        }
    }
}
