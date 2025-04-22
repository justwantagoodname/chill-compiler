package top.voidc.ir.ice.type;

public class IceType implements Comparable<IceType> {
    public static final IceType I1 = new IceType(TypeEnum.I1);
    public static final IceType I8 = new IceType(TypeEnum.I8);
    public static final IceType I32 = new IceType(TypeEnum.I32);
    public static final IceType F32 = new IceType(TypeEnum.F32);
    public static final IceType VOID = new IceType(TypeEnum.VOID);
    public static final IceType STRING = new IceType(TypeEnum.STRING);
    public static final IceType FUNCTION = new IceType(TypeEnum.FUNCTION);

    @Override
    public int compareTo(IceType o) {
        return this.getTypeEnum().compareTo(o.getTypeEnum());
    }

    public enum TypeEnum {
        VOID,
        STRING,
        ARRAY, // 字面数组的值类型
        FUNCTION,
        PTR,
        I1,
        I8,
        I32,
        F32
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
            case I1 -> "i1";
            case I8 -> "i8";
            case I32 -> "i32";
            case F32 -> "float";
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
            case "string" -> STRING;
            default -> throw new IllegalArgumentException("Unknown type: " + literal);
        };
    }

    @Override
    public boolean equals(Object obj) {
        if (obj instanceof IceType) {
            return ((IceType) obj).getTypeEnum() == this.getTypeEnum();
        } else {
            return false;
        }
    }

    public boolean isBoolean() {
        return this.getTypeEnum() == TypeEnum.I1;
    }

    public boolean isInteger() {
        return this.getTypeEnum() == TypeEnum.I32 || this.getTypeEnum() == TypeEnum.I1;
    }

    public boolean isFloat() {
        return this.getTypeEnum() == TypeEnum.F32;
    }

    public boolean isConvertibleTo(IceType target) {
        if (this.equals(target)) {
            return true;
        }
        if (this.isInteger() && target.isFloat()) {
            return true;
        }
        if (this.isFloat() && target.isInteger()) {
            return true;
        }
        if (this.isInteger() && target.isInteger()) {
            return true;
        }
        if (this.isFloat() && target.isFloat()) {
            return true;
        }
        return this.isFloat() && target.isInteger();
    }

    public boolean isVoid() {
        return this.getTypeEnum() == TypeEnum.VOID;
    }

    public boolean isString() {
        return this.getTypeEnum() == TypeEnum.STRING;
    }

    public boolean isArray() {
        return this.getTypeEnum() == TypeEnum.ARRAY;
    }

    public boolean isNumeric() {
        return this.getTypeEnum() == TypeEnum.I32
                || this.getTypeEnum() == TypeEnum.F32
                || this.getTypeEnum() == TypeEnum.I8
                || this.getTypeEnum() == TypeEnum.I1;
    }

    public boolean isPointer() {
        return this.getTypeEnum() == TypeEnum.PTR;
    }

    public int getByteSize() {
        return switch (this.getTypeEnum()) {
            case I1, I8 -> 1;
            case I32, F32 -> 4;
            case VOID -> 0;
            case STRING -> 4; // 指针大小
            case FUNCTION -> 4; // 指针大小
            case ARRAY -> 4; // 指针大小
            case PTR -> 4; // 指针大小
        };
    }

    public IcePtrType<?> asPointer() {
        return (IcePtrType<?>) this;
    }
}
