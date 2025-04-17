package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.misc.Log;

public class IceFcmpInstruction extends IceCmpInstruction {
    private static CmpType getFloatCmpType(CmpType type) {
        return switch (type) {
            case EQ -> CmpType.OEQ;
            case NE -> CmpType.ONE;
            case SLT -> CmpType.OLT;
            case SGE -> CmpType.OGE;
            case SGT -> CmpType.OGT;
            case SLE -> CmpType.OLE;
            default -> type;
        };
    }
    public IceFcmpInstruction(IceBlock parent, String name, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, name, getFloatCmpType(cmpType), lhs, rhs);
        Log.should(lhs.getType().isFloat(), "all should be float");
    }

    public IceFcmpInstruction(IceBlock parent, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, getFloatCmpType(cmpType), lhs, rhs);
        Log.should(lhs.getType().isFloat(), "all should be float");
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = fcmp ").append(cmpType).append(" ")
                .append(getOperand(0).getReferenceName(true)).append(", ")
                .append(getOperand(1).getReferenceName(false));
    }
}
