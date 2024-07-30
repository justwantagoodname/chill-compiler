package top.voidc.ir.type;

public enum IceType {
    I32,
    F32,
    VOID,
    STRING,
    ARRAY,
    FUNCTION,
    ;

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
}
