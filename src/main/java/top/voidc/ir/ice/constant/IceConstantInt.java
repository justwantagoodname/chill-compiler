package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public class IceConstantInt extends IceConstantData {
    private final long value;

    public IceConstantInt(long value) {
        super(IceType.I32);
        this.value = value;
    }

    public long getValue() {
        return (int) value;
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " " : "") + getValue();
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I32 -> this.clone();
            case I1 -> new IceConstantBoolean(value != 0);
            case F32 -> new IceConstantFloat((float) value);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantInt(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IceConstantInt that)) return false;
        return value == that.value;
    }
}
