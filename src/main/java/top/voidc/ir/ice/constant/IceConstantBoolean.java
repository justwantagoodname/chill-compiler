package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public class IceConstantBoolean extends IceConstantData {
    private final boolean value;

    public IceConstantBoolean(String name, boolean value) {
        super(name);
        this.setType(IceType.I1);
        this.value = value;
    }

    public int getValue() {
        return value ? 1 : 0;
    }

    @Override
    public String toString() {
        if (getName() == null) {
            return getType().toString() + ' ' + value;
        }
        return "@" + getName() + " = constant " +  getType() + " " + getValue();
    }

    @Override
    public IceConstantData castTo(IceType targetType) {
        return switch (targetType.getTypeEnum()) {
            case I1 -> this;
            case I32 -> new IceConstantInt(this.getName(), value ? 1 : 0);
            case F32 -> new IceConstantFloat(this.getName(), getValue());
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantBoolean(null, value);
    }
}
