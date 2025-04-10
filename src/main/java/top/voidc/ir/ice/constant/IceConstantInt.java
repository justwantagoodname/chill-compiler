package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceConstantInt extends IceConstantData {
    private final long value;

    public IceConstantInt(String name, long value) {
        super(name);
        this.setType(IceType.I32);
        this.value = value;
    }

    public long getValue() {
        return (int) value;
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
