package top.voidc.backend.instr;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.machine.IceMachineRegister;

public abstract class InstructionPattern <T extends IceValue> {
    int intrinsicCost;

    public InstructionPattern(int intrinsicCost) {
        this.intrinsicCost = intrinsicCost;
    }

    protected int getIntrinsicCost() {
        return intrinsicCost;
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
    public abstract IceMachineRegister emit(InstructionSelector selector, T value);

    public abstract boolean test(InstructionSelector selector, IceValue value);

    public final int getCostForValue(InstructionSelector selector, IceValue value) {
        @SuppressWarnings("unchecked") final var target = (T) value;
        return this.getCost(selector, target);
    }

    public final IceMachineRegister emitForValue(InstructionSelector selector, IceValue value) {
        @SuppressWarnings("unchecked") final var target = (T) value;
        return this.emit(selector, target);
    }
}
