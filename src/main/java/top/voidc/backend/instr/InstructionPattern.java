package top.voidc.backend.instr;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import java.util.function.Predicate;

public abstract class InstructionPattern {
    int intrinsicCost;

    public InstructionPattern(int intrinsicCost) {
        this.intrinsicCost = intrinsicCost;
    }

    protected int getIntrinsicCost() {
        return intrinsicCost;
    }

    public int getCost(InstructionSelector selector, IceValue value) {
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
    public abstract IceMachineRegister emit(InstructionSelector selector, IceValue value);

    public abstract boolean test(InstructionSelector selector, IceValue value);
}
