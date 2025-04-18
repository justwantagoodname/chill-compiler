package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public class IceConstantBoolean extends IceConstantData {
    private final boolean value;

    public IceConstantBoolean(boolean value) {
        super(IceType.I1);
        this.setType(IceType.I1);
        this.value = value;
    }

    public int getValue() {
        return value ? 1 : 0;
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I1 -> IceConstantData.create(value);
            case I32 -> new IceConstantInt(value ? 1 : 0);
            case F32 -> new IceConstantFloat(getValue());
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantBoolean(value);
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " " : "") + getValue();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getReferenceName());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof IceConstantBoolean that)) return false;
        return value == that.value;
    }
}
