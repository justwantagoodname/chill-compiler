package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public class IceConstantByte extends IceConstantData {

    private final byte value;

    public IceConstantByte(byte value) {
        super(IceType.I8);
        this.value = value;
    }

    public byte getValue() {
        return value;
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " " : "") + getValue();
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I8 -> this.clone();
            case I1 -> new IceConstantBoolean(value != 0);
            case I32 -> new IceConstantInt(value);
            case F32 -> new IceConstantFloat((float) value);
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantByte(value);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IceConstantByte other)) return false;
        return value == other.value;
    }
}
