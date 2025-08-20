package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

public class IceSelectInstruction extends IceInstruction {
    public IceSelectInstruction(IceBlock parent, String name, IceValue condition, IceValue trueValue, IceValue falseValue) {
        super(parent, name, trueValue.getType());
        validateOperands(condition, trueValue, falseValue);
        addOperand(condition);
        addOperand(trueValue);
        addOperand(falseValue);
    }

    public IceSelectInstruction(IceBlock parent, IceValue condition, IceValue trueValue, IceValue falseValue) {
        super(parent, trueValue.getType());
        validateOperands(condition, trueValue, falseValue);
        addOperand(condition);
        addOperand(trueValue);
        addOperand(falseValue);
    }

    private void validateOperands(IceValue condition, IceValue trueValue, IceValue falseValue) {
        Log.should(condition.getType().equals(IceType.I1), "select condition must be i1");
        Log.should(trueValue.getType().equals(falseValue.getType()), "select values must have same type");
    }

    public IceValue getCondition() {
        return getOperand(0);
    }

    public IceValue getTrueValue() {
        return getOperand(1);
    }

    public IceValue getFalseValue() {
        return getOperand(2);
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName())
               .append(" = select ")
               .append(getCondition().getReferenceName(true)).append(", ")
               .append(getTrueValue().getReferenceName(true)).append(", ")
               .append(getFalseValue().getReferenceName(false));
    }
}
