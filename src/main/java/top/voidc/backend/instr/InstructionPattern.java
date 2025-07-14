package top.voidc.backend.instr;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;

public abstract class InstructionPattern <T extends IceValue> {
    int intrinsicCost;

    public InstructionPattern(int intrinsicCost) {
        this.intrinsicCost = intrinsicCost;
    }

    protected int getIntrinsicCost() {
        return intrinsicCost;
    }

    /**
     * 获取当前指令模式的返回类型
     * 如果指令模式没有返回类型，则返回null
     * <br>
     * <b>默认使用反射实现，如果返回值为null，那一定要重写这个函数并返回null</b>
     * @return 指令模式的返回类型
     */
    public Class<?> getEmittedType() {
        try {
            var emitMethod = this.getClass().getDeclaredMethod("emit", InstructionSelector.class, IceValue.class);
            return emitMethod.getReturnType();
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e); // This should never happen, as all subclasses must implement emit
        }
    }

    public int getCost(InstructionSelector selector, T value) {
        if (value instanceof IceInstruction instruction) {
            return getIntrinsicCost() + instruction.getOperands()
                    .stream().map(selector::select).map(InstructionSelector.MatchResult::cost).reduce(0, Integer::sum);
        }
        return getIntrinsicCost();
    }

    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 选择实际的指令
     * @return 结果所在寄存器
     */
    public abstract IceMachineValue emit(InstructionSelector selector, T value);

    public abstract boolean test(InstructionSelector selector, IceValue value);

    public final int getCostForValue(InstructionSelector selector, IceValue value) {
        @SuppressWarnings("unchecked") final var target = (T) value;
        return this.getCost(selector, target);
    }

    public final IceMachineValue emitForValue(InstructionSelector selector, IceValue value) {
        @SuppressWarnings("unchecked") final var target = (T) value;
        return this.emit(selector, target);
    }
}
