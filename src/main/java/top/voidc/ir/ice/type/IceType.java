package top.voidc.ir.ice.type;

public class IceType {
    public static final IceType I32 = new IceType(TypeEnum.I32);
    public static final IceType F32 = new IceType(TypeEnum.F32);
    public static final IceType VOID = new IceType(TypeEnum.VOID);
    public static final IceType STRING = new IceType(TypeEnum.STRING);
    public static final IceType FUNCTION = new IceType(TypeEnum.FUNCTION);

    public enum TypeEnum {
        I32,
        F32,
        VOID,
        STRING,
        ARRAY,
        FUNCTION,
        PTR
    }

    private final TypeEnum typeEnum;

    public IceType(TypeEnum typeEnum) {
        this.typeEnum = typeEnum;
    }

    public TypeEnum getTypeEnum() {
        return typeEnum;
    }

    @Override
    public String toString() {
        return switch (this.getTypeEnum()) {
            case I32 -> "i32";
            case F32 -> "f32";
            case VOID -> "void";
            case STRING -> "str";
            case ARRAY -> "array";
            case FUNCTION -> "func";
            case PTR -> "ptr";
        };
    }

    public static IceType fromSysyLiteral(String literal) {
        return switch (literal) {
            case "int" -> I32;
            case "float" -> F32;
            case "void" -> VOID;
            default -> throw new IllegalArgumentException("Unknown type: " + literal);
        };
    }

    public static IceType I32() {
        return I32;
    }

    public static IceType F32() {
        return F32;
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IceType) {
            return ((IceType) obj).getTypeEnum() == this.getTypeEnum();
        } else {
            return false;
        }
    }
}
