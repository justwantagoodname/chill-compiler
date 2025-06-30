package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.machine.IceMachineRegister;

public class MemoryInstructionPattern {
    public static class LoadRegFuncParam extends InstructionPattern {

        // TODO 测试一下后面需要改
        public LoadRegFuncParam() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            final var inst = new ARM64Instruction("LOAD_PARAM x");
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return selector.getIceFunction().getParameters().contains(value);
        }
    }
}
