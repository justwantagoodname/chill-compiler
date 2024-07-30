package top.voidc.ir;

import top.voidc.ir.type.IceType;

public class IceConstantInt extends IceConstantData {
    private final long value;

    public IceConstantInt(String name, long value) {
        super(name);
        this.setType(IceType.I32());
        this.value = value;
    }

    public long getValue() {
        return (int) value;
    }

    @Override
    public String toString() {
        if (getName() == null) {
            return String.valueOf(value);
        }

        return String.format("@%s = constant %s %d", getName(), getType(), value);
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I32 -> this;
            case F32 -> new IceConstantFloat(this.getName(), (float) value);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantInt(null, value);
    }
}
