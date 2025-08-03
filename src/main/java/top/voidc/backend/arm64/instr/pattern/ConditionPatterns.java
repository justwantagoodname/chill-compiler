package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceCmpInstruction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.ice.constant.IceConstantFloat;
import top.voidc.misc.Tool;

import static top.voidc.ir.machine.InstructionSelectUtil.canBeReg;
import static top.voidc.ir.machine.InstructionSelectUtil.isImm12;

/**
 * 条件判断指令模式匹配模块 - 支持比较、测试、条件分支和条件选择
 */
public class ConditionPatterns {

    /**
     * 寄存器比较模式（CMP指令）
     * 用于设置条件标志：`cmp x, y`
     */
    public static class ICMPReg extends InstructionPattern<IceCmpInstruction.Icmp> {

        public ICMPReg() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCmpInstruction.Icmp value) {
            // CMP指令不产生结果寄存器，但设置标志寄存器
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var inst = new ARM64Instruction("CMP {x}, {y}", xReg, yReg);
            selector.addEmittedInstruction(inst);

            // 返回null表示不分配结果寄存器
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCmpInstruction.Icmp icmp
                    && canBeReg(selector, icmp.getLhs())
                    && canBeReg(selector, icmp.getRhs());
        }
    }

    /**
     * 寄存器与立即数比较模式
     * `cmp x, #imm`
     */
    public static class CMPImm extends InstructionPattern<IceCmpInstruction.Icmp> {

        public CMPImm() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public int getCost(InstructionSelector selector, IceCmpInstruction.Icmp value) {
            return getIntrinsicCost() + selector.select(value.getLhs()).cost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCmpInstruction.Icmp value) {
            var xReg = selector.emit(value.getLhs());
            var imm = (IceConstantInt) value.getRhs();
            var inst = new ARM64Instruction("CMP {x}, {imm12:y}", xReg, imm);
            selector.addEmittedInstruction(inst);
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCmpInstruction.Icmp icmp
                    && canBeReg(selector, icmp.getLhs())
                    && isImm12(icmp.getRhs());
        }
    }

    /**
     * 条件分支模式
     * 根据标志寄存器状态跳转：`b.{cond} target`
     */
    public static class CondBranch extends InstructionPattern<IceBranchInstruction> {

        public CondBranch() {
            super(1);
        }

        @Override
        public int getCost(InstructionSelector selector, IceBranchInstruction value) {
            return getIntrinsicCost() + selector.select(value.getCondition()).cost();
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceBranchInstruction value) {
            // 首先处理条件操作（比较或测试）
            selector.emit(value.getCondition());

            // 映射IR条件到ARM64条件码
            String condCode = Tool.mapToArm64Condition((IceCmpInstruction) value.getCondition());

            // 获取目标基本块标签
            var trueLabel = selector.getMachineFunction().getMachineBlock(value.getTrueBlock().getName());
            var falseLabel = selector.getMachineFunction().getMachineBlock(value.getFalseBlock().getName());

            // 创建条件分支指令
            selector.addEmittedInstruction(
                    new ARM64Instruction("B." + condCode + " {label:target}", trueLabel));
            selector.addEmittedInstruction(
                    new ARM64Instruction("B {label:target}", falseLabel));

            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceBranchInstruction branch
                    && branch.isConditional()
                    && branch.getCondition() != null;
        }
    }

    /*
      TODO
      与零比较优化模式
      将`cmp x, 0`优化为`cbz/cbnz`指令
     */

    /**
     * 浮点寄存器比较模式（FCMP指令）
     * 用于设置浮点条件标志：`fcmp d0, d1`
     */
    public static class FCMPReg extends InstructionPattern<IceCmpInstruction.Fcmp> {

        public FCMPReg() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCmpInstruction.Fcmp value) {
            var xReg = selector.emit(value.getLhs());
            var yReg = selector.emit(value.getRhs());
            var inst = new ARM64Instruction("FCMP {x}, {y}", xReg, yReg);
            selector.addEmittedInstruction(inst);
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCmpInstruction.Fcmp fcmp
                    && canBeReg(selector, fcmp.getLhs())
                    && canBeReg(selector, fcmp.getRhs());
        }
    }

    /**
     * 浮点寄存器与0比较模式
     * `fcmp d0, #0.0`
     */
    public static class FCMPZ extends InstructionPattern<IceCmpInstruction.Fcmp> {

        public FCMPZ() {
            super(1);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceCmpInstruction.Fcmp value) {
            var xReg = selector.emit(value.getLhs());
            var inst = new ARM64Instruction("FCMP {x}, #0.0", xReg);
            selector.addEmittedInstruction(inst);
            return null;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            return value instanceof IceCmpInstruction.Fcmp fcmp
                    && canBeReg(selector, fcmp.getLhs())
                    && fcmp.getRhs() instanceof IceConstantFloat constantFloat
                    && constantFloat.getValue() == 0.0f;
        }
    }
}
