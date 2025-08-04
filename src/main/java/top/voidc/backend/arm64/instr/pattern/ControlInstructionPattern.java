package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceCallInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.ir.machine.IceMachineRegister.RegisterView;

import java.util.ArrayList;
import java.util.List;

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

            // 添加这里让返回指令假装使用了返回寄存器，方便活跃性分析和活跃区间的计算
            selector.addEmittedInstruction(new ARM64Instruction("RET // iuse: {implicit:ret}", selector.getMachineFunction().getReturnRegister(IceType.I32)));
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

    public static class RetFloat extends InstructionPattern<IceRetInstruction> {

        public RetFloat() {
            super(2);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceRetInstruction value) {
            assert value.getReturnValue().isPresent();
            var retMachineValue = selector.emit(value.getReturnValue().orElseThrow());
            if (retMachineValue instanceof IceMachineRegister.RegisterView) {
                selector.addEmittedInstruction(
                        new ARM64Instruction("FMOV {dst}, {x}", selector.getMachineFunction().getReturnRegister(IceType.F32), retMachineValue));
            } else {
                selector.addEmittedInstruction(
                        new ARM64Instruction("LDR {dst}, {local:src}", selector.getMachineFunction().getReturnRegister(IceType.F32), retMachineValue)
                );
            }

            // 添加这里让返回指令假装使用了返回寄存器，方便活跃性分析和活跃区间的计算
            selector.addEmittedInstruction(new ARM64Instruction("RET // iuse: {implicit:ret}", selector.getMachineFunction().getReturnRegister(IceType.F32)));
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceRetInstruction ret
                    && !ret.isReturnVoid()
                    && ret.getReturnValue().isPresent()
                    && ret.getReturnValue().get().getType().isFloat();
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

        protected record ArgumentInfo(String argTemplate, List<RegisterView> argumentRegisters) {}

        @Override
        public int getCost(InstructionSelector selector, IceCallInstruction value) {
            return getIntrinsicCost() + value.getArguments()
                    .stream().map(selector::select).map(InstructionSelector.MatchResult::cost).reduce(0, Integer::sum);
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceCallInstruction value) {
            selector.getMachineFunction().setHasCall(true);
             var argumentsInfo = emitArguments(selector, value);
            if (value.getUsers().isEmpty() || value.getType().isVoid()) {
                // 如果没有用户使用这个调用的返回值，或者返回值是void类型
                selector.addEmittedInstruction(new ARM64Instruction("BL " + value.getTarget().getName())); // 直接调用就行
            } else {
                // 在 BL 指令中附加隐式的返回寄存器
                var returnReg = selector.getMachineFunction().getReturnRegister(value.getType());
                var targetFunction = value.getTarget();
                var funcName = targetFunction.getName();
                argumentsInfo.argumentRegisters.addFirst(returnReg);
                selector.addEmittedInstruction(new ARM64Instruction(
                        "BL " + funcName + " // idef: {implicit:dst} iuse: [ " + argumentsInfo.argTemplate + "]", argumentsInfo.argumentRegisters.toArray(new RegisterView[0])));

            }
            return handleFunctionReturn(selector, value);
        }

        /**
         * 处理函数参数的共同逻辑
         */
        protected ArgumentInfo emitArguments(InstructionSelector selector, IceCallInstruction value) {
            var argumentTemplateBuilder = new StringBuilder();
            var argumentRegisters = new ArrayList<RegisterView>();

            var arguments = value.getArguments();
            int intArgCount = 0;
            int floatArgCount = 0;
            
            for (int i = 0; i < arguments.size(); i++) {
                var arg = arguments.get(i);
                boolean isFloat = arg.getType().isFloat();
                
                if (arg instanceof IceConstantInt argInt && isImm12(argInt)) {
                    // 立即数参数 (只能是整数)
                    if (intArgCount < 8) {
                        var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + intArgCount)
                                .createView(IceType.I32);
                        selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {imm12:src}", paramReg, argInt));
                        argumentTemplateBuilder.append("{implicit:iarg").append(intArgCount).append("} ");
                        argumentRegisters.add(paramReg);
                    } else {
                        var valueReg = selector.emit(argInt);
                        var argStackSlot = selector.getMachineFunction().allocateArgumentStackSlot(value, i, value.getType());
                        selector.addEmittedInstruction(
                                new ARM64Instruction("STR {src}, {local:slot}", valueReg, argStackSlot));
                    }
                    intArgCount++;
                } else {
                    var resultArg = selector.emit(arg);
                    if (resultArg instanceof IceMachineRegister.RegisterView argReg) {
                        if (isFloat) {
                            // 浮点数参数使用s寄存器
                            if (floatArgCount < 8) {
                                var paramReg = selector.getMachineFunction().getPhysicalRegister("v" + floatArgCount)
                                        .createView(argReg.getType());
                                selector.addEmittedInstruction(new ARM64Instruction("FMOV {dst}, {src}", paramReg, argReg));
                                argumentTemplateBuilder.append("{implicit:farg").append(floatArgCount).append("} ");
                                argumentRegisters.add(paramReg);
                            } else {
                                var argStackSlot = selector.getMachineFunction().allocateArgumentStackSlot(value, i, value.getType());
                                selector.addEmittedInstruction(
                                        new ARM64Instruction("STR {src}, {local:slot}", argReg, argStackSlot));
                            }
                            floatArgCount++;
                        } else {
                            // 整数参数使用x寄存器
                            if (intArgCount < 8) {
                                var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + intArgCount)
                                        .createView(argReg.getType());
                                selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", paramReg, argReg));
                                argumentTemplateBuilder.append("{implicit:iarg").append(intArgCount).append("} ");
                                argumentRegisters.add(paramReg);
                            } else {
                                var argStackSlot = selector.getMachineFunction().allocateArgumentStackSlot(value, i, value.getType());
                                selector.addEmittedInstruction(
                                        new ARM64Instruction("STR {src}, {local:slot}", argReg, argStackSlot));
                            }
                            intArgCount++;
                        }
                    } else if (resultArg instanceof IceStackSlot argStackSlotPointer) {
                        // 栈上的参数作为指针加载到x寄存器
                        if (intArgCount < 8) {
                            var paramReg = selector.getMachineFunction().getPhysicalRegister("x" + intArgCount)
                                    .createView(IceType.I64);
                            var tmpReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I64);
                            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, sp, {local-offset:slot}", tmpReg, argStackSlotPointer));
                            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", paramReg, tmpReg));
                            argumentTemplateBuilder.append("{implicit:iarg").append(intArgCount).append("} ");
                            argumentRegisters.add(paramReg);
                        } else {
                            var tmpReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I64);
                            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, sp, {local-offset:slot}", tmpReg, argStackSlotPointer));
                            var argStackSlot = selector.getMachineFunction().allocateArgumentStackSlot(value, i, value.getType());
                            selector.addEmittedInstruction(
                                    new ARM64Instruction("STR {src}, {local:slot}", tmpReg, argStackSlot));
                        }
                        intArgCount++;
                    } else {
                        throw new IllegalStateException();
                    }
                }
            }

            return new ArgumentInfo(argumentTemplateBuilder.toString(), argumentRegisters);
        }

        /**
         * 子类实现的处理返回值的逻辑
         */
        protected abstract IceMachineValue handleFunctionReturn(InstructionSelector selector, IceCallInstruction value);
        
        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCallInstruction call && testReturnType(call);
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
        protected RegisterView handleFunctionReturn(InstructionSelector selector, IceCallInstruction value) {
            if (!value.getUsers().isEmpty()) {
                var resultReg = selector.getMachineFunction().getReturnRegister(value.getType());
                var virtualReg = selector.getMachineFunction().allocateVirtualRegister(value.getTarget().getReturnType());
                return selector
                        .addEmittedInstruction(new ARM64Instruction("MOV {dst}, {src}", virtualReg, resultReg))
                        .getResultReg();
            } else {
                return null; // 未使用的值返回null也不会影响
            }
        }

        @Override
        protected boolean testReturnType(IceCallInstruction call) {
            return call.getType().isInteger();
        }
    }

    /**
     * 处理返回整数的函数调用
     */
    public static class FloatCall extends AbstractCallPattern {
        public FloatCall() {
            super(0);
        }

        @Override
        protected RegisterView handleFunctionReturn(InstructionSelector selector, IceCallInstruction value) {
            if (!value.getUsers().isEmpty()) {
                // 如果有用户使用这个返回值，那么需要将其转换为虚拟寄存器
                var resultReg = selector.getMachineFunction().getReturnRegister(value.getType());
                var virtualReg = selector.getMachineFunction().allocateVirtualRegister(value.getTarget().getReturnType());
                return selector
                        .addEmittedInstruction(new ARM64Instruction("FMOV {dst}, {src}", virtualReg, resultReg))
                        .getResultReg();
            } else {
                return null; // 未使用的值返回null也不会影响
            }
        }

        @Override
        protected boolean testReturnType(IceCallInstruction call) {
            return call.getType().isFloat();
        }
    }

    /**
     * 处理可变参数的函数调用，和标准的AAPCS64标准是一样的只是浮点参数在需要作为double传递，但是已经在IR中处理过了，直接就能用VoidCall FloatCall IntCall 处理
     */
    @Deprecated
    public static class VoidVACall extends AbstractCallPattern {
        public VoidVACall() {
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

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCallInstruction call
                    && call.getType().isVoid()
                    && call.getTarget() instanceof IceExternFunction externFunction
                    && externFunction.isVArgs();
        }
    }
}
