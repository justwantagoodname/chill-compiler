package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.machine.IceMachineRegister;

public class MemoryInstructionPattern {
    public static class LoadRegFuncParam extends InstructionPattern {

        /**
         * 匹配函数参数 然后从物理寄存器移动到虚拟寄存器
         */
        // TODO 测试一下后面需要改
        public LoadRegFuncParam() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceValue value) {
            final var paramReg = selector.getMachineFunction().getRegisterForValue(value);
            if (paramReg == null) {
                // TODO: 内存参数的需要load
                throw new UnsupportedOperationException();
            }
            final var dstReg = selector.getMachineFunction().allocateVirtualRegister(value.getType());
            final var inst = new ARM64Instruction("MOV {dst}, {x}", dstReg, paramReg);

            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceFunction.IceFunctionParameter;
        }
    }
}
