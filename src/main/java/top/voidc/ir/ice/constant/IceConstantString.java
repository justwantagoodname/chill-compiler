package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class IceConstantString extends IceConstantArray {
    private final String value;

    public static List<DataArrayElement> createStringElement(String value) {
        // convert value to byte ArrayList in UTF-8 encoding
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        List<DataArrayElement> elements = new ArrayList<>();
        for (byte b : bytes) {
            elements.add(new DataArrayElement(new IceConstantByte(b), 1));
        }
        elements.add(new DataArrayElement(new IceConstantByte((byte) 0), 1));
        return elements;
    }

    public IceConstantString(String value) {
        super(
                new IceArrayType(IceType.I8,
                        value.getBytes(StandardCharsets.UTF_8).length + 1),
                createStringElement(value));
        this.value = value;
    }

    @Override
    public IceConstantData castTo(IceType type) {
        return switch (type.getTypeEnum()) {
            case STRING -> this.clone();
            case ARRAY -> (IceConstantArray) this.clone();
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

    /**
     * 反正 sysy 不支持字符串转义所以无所谓了
     */
    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " " : "") + " c\"" + value + "\\00\"";
    }
}
