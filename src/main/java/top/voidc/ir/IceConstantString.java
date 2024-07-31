package top.voidc.ir;

import top.voidc.ir.type.IcePtrType;
import top.voidc.ir.type.IceType;

public class IceConstantString extends IceConstantData {
    private static int stringCounter = 0;
    private final String value;
    public IceConstantString(String value) {
        super("str" + stringCounter++);
        this.setType(new IcePtrType<>(IceType.STRING));
        this.value = value;
    }

    @Override
    public IceConstantData castTo(IceType type) {
        return switch (type.getTypeEnum()) {
            case STRING -> this;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return new IceConstantString(value);
    }

    public String getValue() {
        return value;
    }
}
