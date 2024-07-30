package top.voidc.ir;

import top.voidc.ir.type.IceType;

public class IceConstantString extends IceConstantData {
    private static int stringCounter = 0;
    private String content;
    public IceConstantString(String value) {
        super("str" + stringCounter++);
        this.content = value;
    }

    @Override
    public IceConstantData castTo(IceType type) {
        return switch (type) {
            case STRING -> this;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    public String getContent() {
        return content;
    }
}
