package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantBoolean;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.misc.Tool;

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
                                    new ARM64Instruction("ADD {dst}, {x}, {imm12:y}, lsl #12",
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

    public static class PowerMulPattern extends InstructionPattern<IceBinaryInstruction.Mul> {
        public PowerMulPattern() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.Mul value) {
            return getIntrinsicCost() + commutativeApply(value,
                    (lhs, rhs) -> canBeReg(selector, lhs) && isConstInt(rhs),
                    (IceValue lhs, IceConstantInt _) -> selector.select(lhs).cost());
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.Mul mul) {
            return commutativeApply(mul,
                    (lhs, rhs) -> canBeReg(selector, lhs) && isConstInt(rhs),
                    (IceValue lhs, IceConstantInt rhs) -> {
                        var srcReg = selector.emit(lhs);
                        var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
                        
                        // 使用LSL指令替代MUL
                        int shift = Tool.log2((int)rhs.getValue());
                        return selector.addEmittedInstruction(
                            new ARM64Instruction(
                                "LSL {dst}, {src}, {imm:shift}",
                                dstReg, srcReg, IceConstantData.create(shift)
                            )
                        ).getResultReg();
                    });
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (!(value instanceof IceBinaryInstruction.Mul mul)) return false;
            return commutativeTest(mul,
                    (lhs, rhs) -> canBeReg(selector, lhs) 
                            && rhs instanceof IceConstantInt intConst 
                            && Tool.isPowerOfTwo((int)intConst.getValue()));
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
                                    new ARM64Instruction("MUL {dst}, {x}, {imm12:y}, lsl #12",
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

    public static class FMADDInstruction extends InstructionPattern<IceBinaryInstruction.FAdd> {

        public FMADDInstruction() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.FAdd value) {
            return getIntrinsicCost() + commutativeApply(value,
                    (lhs, rhs) -> lhs instanceof IceBinaryInstruction.FMul && canBeReg(selector, rhs),
                    (IceBinaryInstruction.FMul mul, IceValue other) -> selector.select(other).cost()
                            + selector.select(mul.getLhs()).cost()
                            + selector.select(mul.getRhs()).cost());
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.FAdd value) {
            // x * y + z -> dst
            return commutativeApply(value,
                    (lhs, rhs) -> lhs instanceof IceBinaryInstruction.FMul && canBeReg(selector, rhs),
                    (IceBinaryInstruction.FMul mul, IceValue other) -> {
                        IceMachineRegister.RegisterView xReg = (IceMachineRegister.RegisterView) selector.emit(mul.getLhs()),
                                yReg = (IceMachineRegister.RegisterView) selector.emit(mul.getRhs()),
                                zReg = (IceMachineRegister.RegisterView) selector.emit(other);
                        var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);

                        return selector.addEmittedInstruction(
                                new ARM64Instruction("FMADD {dst}, {x}, {y}, {z}", dstReg, xReg, yReg, zReg)
                        ).getResultReg();
                    });
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (value instanceof IceBinaryInstruction.FAdd addNode) {
                return commutativeTest(addNode,
                        (lhs, rhs) -> lhs instanceof IceBinaryInstruction.FMul && canBeReg(selector, rhs));
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
                    new ARM64Instruction("MSUB {dst}, {y}, {z}, {x}", dstReg, yReg, zReg, xReg)).getResultReg();
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
     * 寄存器乘减模式，注意这个指令不满足交换律
     * x - y * z -> dst
     */
    public static class FMSUBInstruction extends InstructionPattern<IceBinaryInstruction.FSub> {
        public FMSUBInstruction() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBinaryInstruction.FSub value) {
            var mulNode = (IceBinaryInstruction.FMul)value.getRhs();
            return getIntrinsicCost() + selector.select(value.getLhs()).cost()
                    + selector.select(mulNode.getLhs()).cost() + selector.select(mulNode.getRhs()).cost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.FSub value) {
            // x - y * z -> dst
            var xReg = selector.emit(value.getLhs());
            var mulNode = (IceBinaryInstruction.FMul) value.getRhs();
            var yReg = selector.emit(mulNode.getLhs());
            var zReg = selector.emit(mulNode.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FMSUB {dst}, {y}, {z}, {x}", dstReg, yReg, zReg, xReg)).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (value instanceof IceBinaryInstruction.FSub subNode) {
                var lhs = subNode.getLhs();
                var rhs = subNode.getRhs();
                return canBeReg(selector, lhs) && rhs instanceof IceBinaryInstruction.FMul;
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
        public int getCost(InstructionSelector selector, IceBinaryInstruction.Sub value) {
            return getIntrinsicCost() + selector.select(value.getLhs()).cost();
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
    public static class SDIVTwoReg extends InstructionPattern<IceBinaryInstruction.SDiv> {

        public SDIVTwoReg() {
            super(3);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.SDiv value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var inst = new ARM64Instruction("SDIV {dst}, {x}, {y}", dstReg, xReg, yReg);
            return selector.addEmittedInstruction(inst).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.SDiv divNode
                    && canBeReg(selector, divNode.getLhs())
                    && canBeReg(selector, divNode.getRhs());
        }
    }

    /**
     * 除以2的幂的优化模式：用ASR替代SDIV
     */
    public static class PowerDivPattern extends InstructionPattern<IceBinaryInstruction.SDiv> {
        public PowerDivPattern() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.SDiv div) {
            var lhs = selector.emit(div.getLhs());
            var rhs = (IceConstantInt)div.getRhs();
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var tempReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            
            int shift = Tool.log2(rhs.getValue());
            int offset = (1 << shift) - 1;

            // 比较源操作数是否小于0
            selector.addEmittedInstruction(
                new ARM64Instruction("CMP {src}, #0", lhs)
            );

            // 计算带偏移量的输入
            if (Tool.isImm12(offset)) {
                selector.addEmittedInstruction(
                    new ARM64Instruction("ADD {dst}, {src}, {imm:offset}", tempReg, lhs, IceConstantData.create(offset))
                );
            } else {
                for (var inst : LoadAndStorePattern.ImmediateLoader.loadImmediate32(tempReg, offset)) {
                    selector.addEmittedInstruction(inst);
                }
                selector.addEmittedInstruction(
                    new ARM64Instruction("ADD {dst}, {src}, {offset}", tempReg, lhs, tempReg)
                );
            }
            
            
            // 根据符号选择是否使用带偏移的输入
            // 如果是负数(LT)选择带偏移的值，否则使用原始值
            selector.addEmittedInstruction(
                new ARM64Instruction("CSEL {dst}, {x}, {y}, LT", tempReg, tempReg, lhs)
            );
            
            // 对选择后的值进行一次算术右移
            return selector.addEmittedInstruction(
                new ARM64Instruction("ASR {dst}, {src}, {imm:shift}", dstReg, tempReg, IceConstantData.create(shift))
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.SDiv div
                && canBeReg(selector, div.getLhs())
                && div.getRhs() instanceof IceConstantInt rhs
                && Tool.isPowerOfTwo((int)rhs.getValue());
        }
    }

    public static class NEGReg extends InstructionPattern<IceNegInstruction> {

        public NEGReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceNegInstruction value) {
            if (value.getType().isFloat()) {
                return selector.addEmittedInstruction(new ARM64Instruction("FNEG {dst}, {x}",
                        selector.getMachineFunction().allocateVirtualRegister(IceType.F32),
                        selector.emit(value.getOperand()))).getResultReg();
            } else {
                return selector.addEmittedInstruction(new ARM64Instruction("NEG {dst}, {x}",
                        selector.getMachineFunction().allocateVirtualRegister(IceType.I32),
                        selector.emit(value.getOperand()))).getResultReg();
            }
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceNegInstruction;
        }
    }

    /**
     * 浮点加法指令模式：`x + y -> dst`
     */
    public static class FADDTwoReg extends InstructionPattern<IceBinaryInstruction.FAdd> {

        public FADDTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.FAdd value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FADD {dst}, {x}, {y}", dstReg, xReg, yReg)
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.FAdd faddNode
                    && canBeReg(selector, faddNode.getLhs())
                    && canBeReg(selector, faddNode.getRhs());
        }
    }

    /**
     * 浮点减法指令模式：`x - y -> dst`
     */
    public static class FSUBTwoReg extends InstructionPattern<IceBinaryInstruction.FSub> {

        public FSUBTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.FSub value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FSUB {dst}, {x}, {y}", dstReg, xReg, yReg)
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.FSub fsubNode
                    && canBeReg(selector, fsubNode.getLhs())
                    && canBeReg(selector, fsubNode.getRhs());
        }
    }

    /**
     * 浮点乘法指令模式：`x * y -> dst`
     */
    public static class FMULTwoReg extends InstructionPattern<IceBinaryInstruction.FMul> {

        public FMULTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.FMul value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FMUL {dst}, {x}, {y}", dstReg, xReg, yReg)
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.FMul fmulNode
                    && canBeReg(selector, fmulNode.getLhs())
                    && canBeReg(selector, fmulNode.getRhs());
        }
    }

    /**
     * 浮点除法指令模式：`x / y -> dst`
     */
    public static class FDIVTwoReg extends InstructionPattern<IceBinaryInstruction.FDiv> {

        public FDIVTwoReg() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBinaryInstruction.FDiv value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FDIV {dst}, {x}, {y}", dstReg, xReg, yReg)
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBinaryInstruction.FDiv fdivNode
                    && canBeReg(selector, fdivNode.getLhs())
                    && canBeReg(selector, fdivNode.getRhs());
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

    public static class ZextCMPBoolToInt extends InstructionPattern<IceConvertInstruction> {
        public ZextCMPBoolToInt() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConvertInstruction value) {
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var cmp = (IceCmpInstruction) value.getOperand();
            selector.select(cmp); // 确保操作数被选择
            selector.emit(cmp);
            selector.addEmittedInstruction(
                    new ARM64Instruction("CSET {dst}, " + Tool.mapToArm64Condition(cmp), dstReg)
            );
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConvertInstruction convertInstruction
                    && convertInstruction.getOperand().getType().isBoolean()
                    && convertInstruction.getOperand() instanceof IceCmpInstruction
                    && convertInstruction.getType().equals(IceType.I32);
        }
    }

    public static class ZextCMPBoolToFloat extends InstructionPattern<IceConvertInstruction> {
        public ZextCMPBoolToFloat() {
            super(3);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConvertInstruction value) {
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            var tempIntReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            var cmp = (IceCmpInstruction) value.getOperand();

            selector.select(cmp); // 确保操作数被选择
            selector.emit(cmp);
            selector.addEmittedInstruction(
                    new ARM64Instruction("CSET {dst}, " + Tool.mapToArm64Condition(cmp), tempIntReg)
            );
            selector.addEmittedInstruction(
                    new ARM64Instruction("SCVTF {dst}, {src}", dstReg, tempIntReg) // 将整数转换为浮点数
            );
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConvertInstruction convertInstruction
                    && convertInstruction.getOperand().getType().isBoolean()
                    && convertInstruction.getOperand() instanceof IceCmpInstruction
                    && convertInstruction.getType().equals(IceType.F32);
        }
    }


    public static class ZextBoolToInt extends InstructionPattern<IceConvertInstruction> {
        public ZextBoolToInt() {
            super(0);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConvertInstruction value) {
            var inner = (IceMachineRegister.RegisterView) selector.emit(value.getOperand());
            return inner.getRegister().createView(IceType.I32);
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConvertInstruction convertInstruction
                    && convertInstruction.getOperand().getType().isBoolean()
                    && !(convertInstruction.getOperand() instanceof IceConstantData)
                    && convertInstruction.getType().equals(IceType.I32)
                    && canBeReg(selector, convertInstruction.getOperand());
        }
    }

    public static class ZextBoolImmToInt extends InstructionPattern<IceConvertInstruction> {
        public ZextBoolImmToInt() {
            super(0);
        }

        @Override
        public int getCost(InstructionSelector selector, IceConvertInstruction value) {
            var inner = (IceConstantBoolean) value.getOperand();
            if (inner.equals(IceConstantData.create(true))) {
                // 如果是 true，则返回 1
                return 1;
            } else {
                return 0;
            }
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConvertInstruction value) {
            var inner = (IceConstantBoolean) value.getOperand();
            if (inner.equals(IceConstantData.create(true))) {
                // true
                var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
                return selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, #1", dstReg)).getResultReg();
            } else {
                // false
                return selector.getMachineFunction().getZeroRegister(IceType.I32);
            }
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConvertInstruction convertInstruction
                    && convertInstruction.getOperand().getType().isBoolean()
                    && convertInstruction.getOperand() instanceof IceConstantBoolean
                    && convertInstruction.getType().equals(IceType.I32);
        }
    }

    /**
     * 整数转浮点数模式
     */
    public static class IntToFloat extends InstructionPattern<IceConvertInstruction> {
        public IntToFloat() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConvertInstruction value) {
            var inner = selector.emit(value.getOperand());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("SCVTF {dst}, {src}", dstReg, inner)
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConvertInstruction convertInstruction
                    && convertInstruction.getOperand().getType().isInteger()
                    && !convertInstruction.getOperand().getType().isBoolean()
                    && canBeReg(selector, convertInstruction.getOperand())
                    && convertInstruction.getType().isFloat();
        }
    }

    /**
     * 浮点数转整数模式
     */
    public static class FloatToInt extends InstructionPattern<IceConvertInstruction> {
        public FloatToInt() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConvertInstruction value) {
            var inner = selector.emit(value.getOperand());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I32);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FCVTZS {dst}, {src}", dstReg, inner)
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConvertInstruction convertInstruction
                    && convertInstruction.getOperand().getType().isFloat()
                    && convertInstruction.getType().isInteger()
                    && !convertInstruction.getType().isBoolean();
        }
    }

    public static class FloatToDouble extends InstructionPattern<IceConvertInstruction> {
        public FloatToDouble() {
            super(1);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceConvertInstruction value) {
            var inner = selector.emit(value.getOperand());
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(IceType.F64);
            return selector.addEmittedInstruction(
                    new ARM64Instruction("FCVT {dst}, {src}", dstReg, inner)
            ).getResultReg();
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceConvertInstruction convertInstruction
                    && convertInstruction.getOperand().getType().equals(IceType.F32)
                    && convertInstruction.getType().equals(IceType.F64);
        }
    }
}
