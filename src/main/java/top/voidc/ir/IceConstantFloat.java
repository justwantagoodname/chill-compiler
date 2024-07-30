package top.voidc.ir;

import top.voidc.ir.type.IceType;

public class IceConstantFloat extends IceConstantData {
    private final double value;

    public IceConstantFloat(String name, double value) {
        super(name);
        this.type = IceType.F32();
        this.value = value;
    }

    public double getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "IceConstantFloat with value: " + value;
    }

    public IceConstantData castTo(IceType targetType) {
        return switch (targetType) {
            case I32 -> new IceConstantInt(this.getName(), (long) value);
            case F32 -> this;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }
}
