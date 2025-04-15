package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.misc.Log;

public class IceIcmpInstruction extends IceCmpInstruction {
    public IceIcmpInstruction(IceBlock parent, String name, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, name, cmpType, lhs, rhs);
        Log.should(lhs.getType().isInteger(), "all should be integer");
    }

    public IceIcmpInstruction(IceBlock parent, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, cmpType, lhs, rhs);
        Log.should(lhs.getType().isInteger(), "all should be integer");
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = icmp ").append(cmpType).append(" ")
                .append(getOperand(0).getReferenceName(true)).append(", ")
                .append(getOperand(1).getReferenceName(false));
    }
}
