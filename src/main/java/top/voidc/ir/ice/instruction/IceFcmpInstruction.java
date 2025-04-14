package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.misc.Log;

public class IceFcmpInstruction extends IceCmpInstruction {
    public IceFcmpInstruction(IceBlock parent, String name, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, name, cmpType, lhs, rhs);
        Log.should(lhs.getType().isFloat(), "all should be integer");
    }

    public IceFcmpInstruction(IceBlock parent, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, cmpType, lhs, rhs);
        Log.should(lhs.getType().isFloat(), "all should be integer");
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = fcmp ").append(cmpType).append(" ")
                .append(getOperand(0).getReferenceName()).append(", ")
                .append(getOperand(1).getReferenceName());
    }
}
