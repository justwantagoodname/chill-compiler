package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

public class ControlInstructionPattern {
    public static class RetVoid extends InstructionPattern {

        public RetVoid() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            final var instr = new ARM64Instruction("RET");
            selector.addEmittedInstruction(instr);
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceRetInstruction ret && ret.isReturnVoid();
        }
    }

    public static class RetInteger extends InstructionPattern {

        public RetInteger() {
            super(2);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            var ret = (IceRetInstruction) value;
            selector.emit(ret.getReturnValue().get());
            var instr1 = new ARM64Instruction("MOV w0, x");
            var instr2 = new ARM64Instruction("RET");
            selector.addEmittedInstruction(instr1);
            selector.addEmittedInstruction(instr2);
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceRetInstruction ret
                    && ret.getReturnValue().isPresent()
                    && ret.getReturnValue().get().getType().isInteger();
        }
    }
}
