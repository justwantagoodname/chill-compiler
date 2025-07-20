package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IceNegInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

import static top.voidc.ir.machine.InstructionSelectUtil.*;

public class ArithmaticInstructionPattern {

    /**
     * AArch64的 Bitmask 立即数格式
     * @return 是否能由 bitmask-immediate 表示
     */
    public static boolean isValidBitmaskImmediate(int val) {
        if (val == 0 || val == ~0) return false;

        int lsZeroAfterOne = Integer.numberOfTrailingZeros(val & (val + 1));
        int norm = Integer.rotateRight(val, lsZeroAfterOne);

        int zeros = Integer.numberOfLeadingZeros(norm);
        int ones = Integer.numberOfTrailingZeros(~norm);
        int size = zeros + ones;
        if (size < 2 || size > 32) return false;

        if ((size & (size - 1)) != 0) return false; // size must be power of 2

        return Integer.rotateRight(val, size) == val; // must repeat evenly
    }

    public static boolean isValidAddImmediate(IceConstantInt value) {
        var imm = (int) value.getValue();
        // AArch64 ADD/SUB immediate: 12-bit unsigned, 可选左移12位
        if (imm < 0) return false;

        // 直接范围：0…4095
        if (imm <= 0xFFF) return true;

        // 左移12位后：imm12 << 12 -> 范围 0x00001000…0xFFF000
        return (imm & 0xFFF) == 0 && (imm >> 12) <= 0xFFF;
    }

    public static boolean isImmediateNeedLSL(IceConstantInt value) {
        var imm = (int) value.getValue();
        // AArch64 ADD/SUB immediate: 12-bit unsigned, 可选左移12位
        if (imm < 0) return false;

        // 直接范围：0…4095
        if (imm <= 0xFFF) return false;

        // 左移12位后：imm12 << 12 -> 范围 0x00001000…0xFFF000
        return (imm & 0xFFF) == 0 && (imm >> 12) <= 0xFFF;
    }

    public static class ADDTwoReg extends InstructionPattern<IceBinaryInstruction.Add> {

        public ADDTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Add value) {
            // x + y = dst
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("ADD {dst}, {x}, {y}", dstReg, xReg, yReg)
            ).getResultReg();
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
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Add value) {
            return commutativeApply(value,
                    (lhs, rhs) -> canBeReg(selector, lhs) && isConstInt(rhs),
                    (IceValue lhs, IceConstantInt rhs) -> {
                        if (isImmediateNeedLSL(rhs)) {
                            // 如果立即数需要左移12位，则使用 ADD {dst}, {x}, {imm12:y} 的形式
                            return selector.addEmittedInstruction(
                                    new ARM64Instruction("ADD {dst}, {x}, {imm12:y} lsl #12",
                                            selector.getMachineFunction().allocateVirtualRegister(IceType.I32), selector.emit(lhs), rhs))
                                    .getResultReg();
                        } else {
                            return selector.addEmittedInstruction(
                                    new ARM64Instruction("ADD {dst}, {x}, {imm12:y}",
                                            selector.getMachineFunction().allocateVirtualRegister(IceType.I32), selector.emit(lhs), rhs))
                                    .getResultReg();
                        }
                    });
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Add add &&
                    commutativeTest(add,
                            // 确保 lhs 不会被 wzr 替换因为 ADD dst, wzr, imm12 是非法的
                            (lhs, rhs) -> canBeReg(selector, lhs) && !(lhs.equals(IceConstantData.create(0)))
                                    && rhs instanceof IceConstantInt intConst && isValidAddImmediate(intConst));
        }
    }

    public static class MULTwoReg extends InstructionPattern<IceBinaryInstruction.Mul> {

        public MULTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Mul value) {
            // x * y -> dst
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("MUL {dst}, {x}, {y}", dstReg, xReg, yReg)).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Mul mulNode
                    && canBeReg(selector, mulNode.getLhs())
                    && canBeReg(selector, mulNode.getRhs());
        }
    }

    public static class MULImm extends InstructionPattern<IceBinaryInstruction.Mul> {

        public MULImm() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.Mul value) {
            return getIntrinsicCost() + commutativeApply(value,
                    (lhs, rhs) -> canBeReg(selector, lhs) && isConstInt(rhs),
                    (IceValue lhs, IceConstantInt _) -> selector.select(lhs).cost());
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Mul value) {
            // TODO: 需要修改
            return commutativeApply(value,
                    (lhs, rhs) -> canBeReg(selector, lhs) && isConstInt(rhs),
                    (IceValue lhs, IceConstantInt rhs) -> {
                        if (isImmediateNeedLSL(rhs)) {
                            // 如果立即数需要左移12位，则使用 ADD {dst}, {x}, {imm12:y} 的形式
                            return selector.addEmittedInstruction(
                                    new ARM64Instruction("MUL {dst}, {x}, {imm12:y} lsl #12",
                                            selector.getMachineFunction().allocateVirtualRegister(IceType.I32), selector.emit(lhs), rhs))
                                    .getResultReg();
                        } else {
                            return selector.addEmittedInstruction(
                                    new ARM64Instruction("MUL {dst}, {x}, {imm12:y}",
                                            selector.getMachineFunction().allocateVirtualRegister(IceType.I32), selector.emit(lhs), rhs))
                                    .getResultReg();
                        }
                    });

        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Mul mul &&
                    commutativeTest(mul,
                            // 确保 lhs 不会被 wzr 替换因为 MUL dst, wzr, imm12 是非法的
                            (lhs, rhs) -> canBeReg(selector, lhs) && !(lhs.equals(IceConstantData.create(0)))
                                    && rhs instanceof IceConstantInt intConst && isValidAddImmediate(intConst));
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
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Add value) {
            // x * y + z -> dst
            return commutativeApply(value,
                    (lhs, rhs) -> lhs instanceof IceBinaryInstruction.Mul && canBeReg(selector, rhs),
                    (IceBinaryInstruction.Mul mul, IceValue other) -> {
                        IceMachineRegister.RegisterView xReg = (IceMachineRegister.RegisterView) selector.emit(mul.getLhs()),
                                yReg = (IceMachineRegister.RegisterView) selector.emit(mul.getRhs()),
                                zReg = (IceMachineRegister.RegisterView) selector.emit(other);
                        var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);

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
     * 寄存器乘减模式，注意这个指令不满足交换律
     * x - y * z -> dst
     */
    public static class MSUBInstruction extends InstructionPattern<IceBinaryInstruction.Sub> {
        public MSUBInstruction() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            var mulNode = (IceBinaryInstruction.Mul)value.getRhs();
            return getIntrinsicCost() + selector.select(value.getLhs()).cost()
                    + selector.select(mulNode.getLhs()).cost() + selector.select(mulNode.getRhs()).cost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            // x - y * z -> dst
            var xReg = selector.emit(value.getLhs());
            var mulNode = (IceBinaryInstruction.Mul) value.getRhs();
            var yReg = selector.emit(mulNode.getLhs());
            var zReg = selector.emit(mulNode.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("MSUB {dst}, {x}, {y}, {z}", dstReg, xReg, yReg, zReg)).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (value instanceof IceBinaryInstruction.Sub subNode) {
                var lhs = subNode.getLhs();
                var rhs = subNode.getRhs();
                return canBeReg(selector, lhs) && rhs instanceof IceBinaryInstruction.Mul;
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
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("SUB {dst}, {x}, {y}", dstReg, xReg, yReg);
            return selector.addEmittedInstruction(inst).getResultReg();
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
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            var xReg = selector.emit(value.getLhs());
            var imm = (IceConstantInt) value.getRhs();
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("SUB {dst}, {x}, {imm12:y}", dstReg, xReg, imm);
            return selector.addEmittedInstruction(inst).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Sub subNode
                    && canBeReg(selector, subNode.getLhs())
                    && isImm12(subNode.getRhs());
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
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Div value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("SDIV {dst}, {x}, {y}", dstReg, xReg, yReg);
            return selector.addEmittedInstruction(inst).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Div divNode
                    && canBeReg(selector, divNode.getLhs())
                    && canBeReg(selector, divNode.getRhs());
        }
    }

    public static class NEGReg extends InstructionPattern<IceNegInstruction> {

        public NEGReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceNegInstruction value) {
            return selector.addEmittedInstruction(new ARM64Instruction("NEG {dst}, {x}",
                    selector.getMachineFunction().allocateVirtualRegister(IceType.I32),
                    selector.emit(value.getOperand()))).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceNegInstruction;
        }
    }

    /**
     * 模运算模式：`x % y = x - (x / y) * y`
     */
    public static class SMODTwoReg extends InstructionPattern<IceBinaryInstruction.Mod> {

        public SMODTwoReg() {
            super(2);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Mod value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var divReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            selector.addEmittedInstruction(
                    new ARM64Instruction("SDIV {dst}, {x}, {y}", divReg, xReg, yReg));
            return selector.addEmittedInstruction(new ARM64Instruction("MSUB {dst}, {x}, {y}, {z}", dstReg, divReg, yReg, xReg)).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.Mod modNode
                    && canBeReg(selector, modNode.getLhs())
                    && canBeReg(selector, modNode.getRhs());
        }
    }
}
