package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceConstantFloat extends IceConstantData {
    private final double value;

    public IceConstantFloat(double value) {
        super(IceType.F32);
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String getReferenceName(boolean withType) {
        final var bits = Double.doubleToRawLongBits(value);
        return (withType ? getType() + " " : "") + " 0x" + Long.toHexString(bits).toUpperCase();
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I32 -> new IceConstantInt((long) value);
            case I1 -> new IceConstantBoolean(getValue() != 0);
            case F32 -> this.clone();
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantFloat(value);
    }
}
