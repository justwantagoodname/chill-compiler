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
import top.voidc.ir.machine.IceStackSlot;

import static top.voidc.ir.machine.InstructionSelectUtil.*;

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
            assert value.getReturnValue().isPresent();
            if (isImm12(value.getReturnValue().get())) {
                selector.addEmittedInstruction(
                        new ARM64Instruction("MOV {dst}, {imm12:src}", selector.getMachineFunction().getReturnRegister(IceType.I32), (IceConstantInt) value.getReturnValue().get()));
            } else {
                var retMachineValue = selector.emit(value.getReturnValue().orElseThrow());
                if (retMachineValue instanceof IceMachineRegister.RegisterView) {
                    selector.addEmittedInstruction(
                            new ARM64Instruction("MOV {dst}, {x}", selector.getMachineFunction().getReturnRegister(IceType.I32), retMachineValue));
                } else {
                    selector.addEmittedInstruction(
                            new ARM64Instruction("LDR {dst}, {local:src}", selector.getMachineFunction().getReturnRegister(IceType.I32), retMachineValue)
                    );
                }
            }

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

    /**
     * 函数调用的抽象基类，处理共同的参数处理逻辑
     */
    public abstract static class AbstractCallPattern extends InstructionPattern<IceCallInstruction> {
        protected AbstractCallPattern(int intrinsicCost) {
            super(intrinsicCost);
        }

        @Override
        public int getCost(InstructionSelector selector, IceCallInstruction value) {
            return getIntrinsicCost() + value.getArguments()
                    .stream().map(selector::select).map(InstructionSelector.MatchResult::cost).reduce(0, Integer::sum);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceCallInstruction value) {
            selector.getMachineFunction().setHasCall(true);
            emitArguments(selector, value);
            selector.addEmittedInstruction(new ARM64Instruction("BL " + value.getTarget().getName()));
            return handleFunctionReturn(selector, value);
        }

        /**
         * 处理函数参数的共同逻辑
         */
        protected void emitArguments(InstructionSelector selector, IceCallInstruction value) {
            var arguments = value.getArguments();
            var curArg = 0;
            for (; curArg < arguments.size(); curArg++) {
                var arg = arguments.get(curArg);
                if (arg instanceof IceConstantInt argInt && isImm12(argInt)) {
                    // 立即数参数
                    var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + curArg)
                            .createView(IceType.I32);
                    if (curArg < 8) {
                        selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {imm12:src}", paramReg, argInt));
                    } else {
                        // 对于超过 8 个参数的情况，需要将其存储到栈上
                        var valueReg = selector.emit(argInt);
                        var argStackSlot = selector.getMachineFunction().allocateArgumentStackSlot(value, curArg, value.getType());
                        selector.addEmittedInstruction(
                                new ARM64Instruction("STR {src}, {local:slot}", valueReg, argStackSlot));
                    }
                } else {
                    var resultArg = selector.emit(arg);
                    if (resultArg instanceof IceMachineRegister.RegisterView argReg) {
                        var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + curArg)
                                .createView(argReg.getType());
                        if (curArg < 8) {
                            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", paramReg, argReg));
                        } else {
                            // 对于超过 8 个参数的情况，需要将其存储到栈上
                            var argStackSlot = selector.getMachineFunction().allocateArgumentStackSlot(value, curArg, value.getType());
                            selector.addEmittedInstruction(
                                    new ARM64Instruction("STR {src}, {local:slot}", argReg, argStackSlot));
                        }
                    } else if (resultArg instanceof IceStackSlot argStackSlotPointer) {
                        // 是栈上的参数需要作为指针加载
                        var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + curArg)
                                .createView(IceType.I64);
                        var tmpReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I64);
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, sp, {local-offset:slot}", tmpReg, argStackSlotPointer));

                        if (curArg < 8) {
                            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", paramReg, tmpReg));
                        } else {
                            // 对于超过 8 个参数的情况，需要将其存储到栈上
                            var argStackSlot = selector.getMachineFunction().allocateArgumentStackSlot(value, curArg, value.getType());
                            selector.addEmittedInstruction(
                                    new ARM64Instruction("STR {src}, {local:slot}", tmpReg, argStackSlot));
                        }
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }
        }

        /**
         * 子类实现的处理返回值的逻辑
         */
        protected abstract IceMachineValue handleFunctionReturn(InstructionSelector selector, IceCallInstruction value);
        
        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCallInstruction call &&
                    call.getArguments().stream().allMatch(arg -> canBeReg(selector, arg) || isImm12(arg)) &&
                    testReturnType(call);
        }

        /**
         * 子类实现的返回类型检查
         */
        protected abstract boolean testReturnType(IceCallInstruction call);
    }

    /**
     * 处理无返回值的函数调用
     */
    public static class VoidCall extends AbstractCallPattern {
        public VoidCall() {
            super(0);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        protected IceMachineValue handleFunctionReturn(InstructionSelector selector, IceCallInstruction value) {
            return null; // Void call 不需要处理返回值
        }

        @Override
        protected boolean testReturnType(IceCallInstruction call) {
            return call.getType().isVoid();
        }
    }

    /**
     * 处理返回整数的函数调用
     */
    public static class IntCall extends AbstractCallPattern {
        public IntCall() {
            super(0);
        }

        @Override
        protected IceMachineValue handleFunctionReturn(InstructionSelector selector, IceCallInstruction value) {
            var resultReg = selector.getMachineFunction().getReturnRegister(value.getType());
            var virtualReg = selector.getMachineFunction().allocateVirtualRegister(value.getTarget().getReturnType());
            return selector
                    .addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", virtualReg, resultReg))
                    .getResultReg();
        }

        @Override
        protected boolean testReturnType(IceCallInstruction call) {
            return call.getType().isInteger();
        }
    }
}
