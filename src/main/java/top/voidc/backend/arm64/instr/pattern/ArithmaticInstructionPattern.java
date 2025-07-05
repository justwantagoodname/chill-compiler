package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

import static top.voidc.ir.machine.InstructionSelectUtil.*;

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
                    && canBeReg(selector, addNode.getLhs())
                    && canBeReg(selector, addNode.getRhs());
        }

    }

    public static class ADDImm extends InstructionPattern<IceBinaryInstruction.Add> {

        public ADDImm() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.Add value) {
            return getIntrinsicCost() + commutativeApply(value,
                    (lhs, rhs) -> canBeReg(selector, lhs) && isConstInt(rhs),
                    (IceValue lhs, IceConstantInt _) -> selector.select(lhs).cost());
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Add value) {
            return commutativeApply(value,
                    (lhs, rhs) -> canBeReg(selector, lhs) && isConstInt(rhs),
                    (IceValue lhs, IceConstantInt rhs) ->
                            selector.addEmittedInstruction(
                                    new ARM64Instruction("ADD {dst}, {x}, {imm12:y}",
                                            selector.getMachineFunction().allocateVirtualRegister(IceType.I32), selector.emit(lhs), rhs))
                                    .getResultReg());
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Add add &&
                    commutativeTest(add, (lhs, rhs) -> canBeReg(selector, lhs) && isImm12(rhs));
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
                    && canBeReg(selector, mulNode.getLhs())
                    && canBeReg(selector, mulNode.getRhs());
        }
    }

    public static class MADDInstruction extends InstructionPattern<IceBinaryInstruction.Add> {

        public MADDInstruction() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.Add value) {
            return getIntrinsicCost() + commutativeApply(value,
                    (lhs, rhs) -> lhs instanceof IceBinaryInstruction.Mul && canBeReg(selector, rhs),
                    (IceBinaryInstruction.Mul mul, IceValue other) -> selector.select(other).cost()
                             + selector.select(mul.getLhs()).cost()
                             + selector.select(mul.getRhs()).cost());
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Add value) {
            // x * y + z -> dst
            return commutativeApply(value,
                (lhs, rhs) -> lhs instanceof IceBinaryInstruction.Mul && canBeReg(selector, rhs),
                (IceBinaryInstruction.Mul mul, IceValue other) -> {
                    IceMachineRegister xReg = selector.emit(mul.getLhs()),
                            yReg = selector.emit(mul.getRhs()),
                            zReg = selector.emit(other),
                            dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);

                    return selector.addEmittedInstruction(
                            new ARM64Instruction("MADD {dst}, {x}, {y}, {z}", dstReg, xReg, yReg, zReg)
                    ).getResultReg();
                });
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (value instanceof IceBinaryInstruction.Add addNode) {
                return commutativeTest(addNode,
                        (lhs, rhs) -> lhs instanceof IceBinaryInstruction.Mul && canBeReg(selector, rhs));
            }
            return false;
        }
    }

    /**
     * 寄存器减法模式：`x - y -> dst`
     */
    public static class SUBTwoReg extends InstructionPattern<IceBinaryInstruction.Sub> {

        public SUBTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("SUB {dst}, {x}, {y}", dstReg, xReg, yReg);
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Sub subNode
                    && canBeReg(selector, subNode.getLhs())
                    && canBeReg(selector, subNode.getRhs());
        }
    }

    /**
     * 寄存器减立即数模式：`x - imm12 -> dst`
     */
    public static class SUBImm extends InstructionPattern<IceBinaryInstruction.Sub> {

        public SUBImm() {
            super(1);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            var xReg = selector.emit(value.getLhs());
            var imm = (IceConstantInt) value.getRhs();
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("SUB {dst}, {x}, {imm12:y}", dstReg, xReg, imm);
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Sub subNode
                    && canBeReg(selector, subNode.getLhs())
                    && isImm12(subNode.getRhs());
        }
    }

    /**
     * 立即数减寄存器模式：`imm12 - x -> dst`
     * 使用反向减法指令（ARM64没有直接支持，需加载立即数到临时寄存器）
     */
    public static class ImmSUBAlias extends InstructionPattern<IceBinaryInstruction.Sub> {

        public ImmSUBAlias() {
            super(2);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            var imm = (IceConstantInt) value.getLhs();
            var xReg = selector.emit(value.getRhs());

            // 加载立即数到临时寄存器
            var tempReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var movInst = new ARM64Instruction("MOV {temp}, {imm}", tempReg, imm);
            selector.addEmittedInstruction(movInst);

            // 执行减法
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var subInst = new ARM64Instruction("SUB {dst}, {temp}, {x}", dstReg, tempReg, xReg);
            selector.addEmittedInstruction(subInst);
            return subInst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Sub subNode
                    && isImm12(subNode.getLhs())
                    && canBeReg(selector, subNode.getRhs());
        }
    }

    /**
     * 除法模式：`x / y -> dst`
     */
    public static class SDIVTwoReg extends InstructionPattern<IceBinaryInstruction.Div> {

        public SDIVTwoReg() {
            super(3);
        }

        @Override
        public IceMachineRegister emit(InstructionSelector selector, IceBinaryInstruction.Div value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("SDIV {dst}, {x}, {y}", dstReg, xReg, yReg);
            selector.addEmittedInstruction(inst);
            return inst.getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Div divNode
                    && canBeReg(selector, divNode.getLhs())
                    && canBeReg(selector, divNode.getRhs());
        }
    }

}
