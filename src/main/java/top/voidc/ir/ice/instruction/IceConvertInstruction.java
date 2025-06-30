package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

public class IceConvertInstruction extends IceInstruction {
    public IceConvertInstruction(IceBlock parent,
                                 String name,
                                 IceType type,
                                 IceValue operand) {
        super(parent, name, type);
        addOperand(operand);
    }

    public IceConvertInstruction(IceBlock parent,
                                 IceType type,
                                 IceValue operand) {
        super(parent, type);
        addOperand(operand);
    }

    private String getConvertOp() {
        IceType fromType = getOperand(0).getType();
        IceType toType = getType();

        if (fromType.isInteger() && toType.isInteger()) {
            if (fromType == IceType.I1) {
                return "zext"; // i1 -> i8/i32
            } else if (toType == IceType.I1) {
                return "trunc"; // i8/i32 -> i1
            } else if (fromType == IceType.I8 && toType == IceType.I32) {
                return "sext"; // i8 -> i32
            } else if (fromType == IceType.I32 && toType == IceType.I8) {
                return "trunc"; // i32 -> i8
            }
        } else if (fromType.isInteger() && toType == IceType.F32) {
            return "sitofp"; // i1/i8/i32 -> f32
        } else if (fromType == IceType.F32 && toType.isInteger()) {
            return "fptosi"; // f32 -> i1/i8/i32
        } else if (fromType == IceType.F32 && toType == IceType.F64) {
            return "fpext";
        }
        
        throw new IllegalStateException("Unsupported type conversion from " + fromType + " to " + toType);
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = ").append(getConvertOp())
                .append(" ").append(getOperand(0).getReferenceName())
                .append(" to ").append(getType());
    }
}
