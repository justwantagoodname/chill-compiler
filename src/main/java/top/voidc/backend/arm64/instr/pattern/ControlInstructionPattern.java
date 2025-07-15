package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceCallInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

import static top.voidc.ir.machine.InstructionSelectUtil.canBeReg;
import static top.voidc.ir.machine.InstructionSelectUtil.isImm12;

public class ControlInstructionPattern {
    public static class RetVoid extends InstructionPattern<IceRetInstruction> {

        public RetVoid() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
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
        public Class<?> getEmittedType() {
            return null;
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
                    && !ret.isReturnVoid()
                    && ret.getReturnValue().isPresent()
                    && ret.getReturnValue().get().getType().isInteger();
        }
    }

    public static class BranchUnconditional extends InstructionPattern<IceBranchInstruction> {
        public BranchUnconditional() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
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

    public static class VoidCall extends InstructionPattern<IceCallInstruction> {

        public VoidCall() {
            super(0); // FIXME: 需要确定代价
        }

        @Override
        public int getCost(InstructionSelector selector, IceCallInstruction value) {
            return getIntrinsicCost() + value.getArguments()
                    .stream().map(selector::select).map(InstructionSelector.MatchResult::cost).reduce(0, Integer::sum);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceCallInstruction value) {
            selector.getMachineFunction().setHasCall(true);
            // TODO: 处理浮点数参数
            var arguments = value.getArguments();
            var curArg = 0;
            for (; curArg < arguments.size(); curArg++) {
                if (curArg >= 8) {
                    // ARM64 只支持前 8 个参数通过寄存器传递
                    // TODO: 处理栈传递的参数
                    throw new UnsupportedOperationException();
                }
                var arg = value.getArguments().get(curArg);
                var resultArg = selector.emit(arg);
                var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + curArg)
                        .createView(((IceValue) resultArg).getType());
                if (resultArg instanceof IceMachineRegister.RegisterView argReg) {
                    selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", paramReg, argReg));
                } else if (resultArg instanceof IceConstantInt argInt) {
                    selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, #{imm12:src}", paramReg, argInt));
                } else {
                    throw new IllegalStateException();
                }
            }
            // FIXME: 正确处理函数作为操作数的情况？
            selector.addEmittedInstruction(new ARM64Instruction("BL " + value.getTarget().getName()));
            return null; // Void call does not return a value
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCallInstruction call && call.getType().isVoid() &&
                    call.getArguments().stream().allMatch(arg -> canBeReg(selector, arg) || isImm12(arg));
        }
    }

    public static class IntCall extends InstructionPattern<IceCallInstruction> {

        public IntCall() {
            super(0); // FIXME: 需要确定代价
        }

        @Override
        public int getCost(InstructionSelector selector, IceCallInstruction value) {
            return getIntrinsicCost() + value.getArguments()
                    .stream().map(selector::select).map(InstructionSelector.MatchResult::cost).reduce(0, Integer::sum);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCallInstruction value) {
            selector.getMachineFunction().setHasCall(true);
            // TODO: 处理浮点数参数
            var arguments = value.getArguments();
            var curArg = 0;
            for (; curArg < arguments.size(); curArg++) {
                if (curArg >= 8) {
                    // ARM64 只支持前 8 个参数通过寄存器传递
                    // TODO: 处理栈传递的参数
                    throw new UnsupportedOperationException();
                }
                var arg = value.getArguments().get(curArg);
                var resultArg = selector.emit(arg);
                var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + curArg)
                        .createView(((IceValue) resultArg).getType());
                if (resultArg instanceof IceMachineRegister.RegisterView argReg) {
                    selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", paramReg, argReg));
                } else if (resultArg instanceof IceConstantInt argInt) {
                    selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, #{imm12:src}", paramReg, argInt));
                } else {
                    throw new IllegalStateException();
                }


            }
            // FIXME: 正确处理函数作为操作数的情况？
            selector.addEmittedInstruction(new ARM64Instruction("BL " + value.getTarget().getName()));
            var resultReg = selector.getMachineFunction().getReturnRegister(value.getType());
            var virtualReg = selector.getMachineFunction().allocateVirtualRegister(value.getTarget().getReturnType());
            return selector
                    .addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", virtualReg, resultReg))
                    .getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCallInstruction call && call.getType().isInteger() &&
                    call.getArguments().stream().allMatch(arg -> canBeReg(selector, arg) || isImm12(arg));
        }
    }
}
