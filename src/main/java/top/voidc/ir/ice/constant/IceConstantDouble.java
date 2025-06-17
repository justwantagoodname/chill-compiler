package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public class IceConstantDouble extends IceConstantData {
    private final double value;

    public IceConstantDouble(double value) {
        super(IceType.F64);
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String getReferenceName(boolean withType) {
        final var bits = Double.doubleToRawLongBits(value);
        return (withType ? getType() + " " : "") + "0x" + Long.toHexString(bits).toUpperCase();
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I32 -> new IceConstantInt((long) value);
            case I1 -> new IceConstantBoolean(getValue() != 0);
            case F32 -> new IceConstantFloat((float) value);
            case F64 -> this.clone();
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantDouble(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        // TODO: 也许需要改进所有的字面量立即数比较
        if (!(o instanceof IceConstantDouble that)) return false;
        return Double.compare(that.value, value) == 0;
    }
}
