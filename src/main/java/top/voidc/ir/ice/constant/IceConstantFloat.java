package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceConstantFloat extends IceConstantData {
    private final double value;

    public IceConstantFloat(String name, double value) {
        super(name);
        setType(IceType.F32);
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        if (getName() == null) {
            return '(' + getType().toString() + ' ' + value + ')';
        }

        return String.format("@%s = constant %s %f", getName(), getType(), value);
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I32 -> new IceConstantInt(this.getName(), (long) value);
            case F32 -> this;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantFloat(null, value);
    }


}
