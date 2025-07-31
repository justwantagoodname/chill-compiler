package top.voidc.ir.ice.type;

import java.util.Set;

public class IceType implements Comparable<IceType> {
    public static final IceType I1 = new IceType(TypeEnum.I1);
    public static final IceType I8 = new IceType(TypeEnum.I8);
    public static final IceType I32 = new IceType(TypeEnum.I32);
    public static final IceType I64 = new IceType(TypeEnum.I64);
    public static final IceType F32 = new IceType(TypeEnum.F32);
    public static final IceType F64 = new IceType(TypeEnum.F64);
    public static final IceType VOID = new IceType(TypeEnum.VOID);
    public static final IceType STRING = new IceType(TypeEnum.STRING);
    public static final IceType ANY = new IceType(TypeEnum.ANY);
    public static final IceType FUNCTION = new IceType(TypeEnum.FUNCTION);

    @Override
    public int compareTo(IceType o) {
        return this.getTypeEnum().compareTo(o.getTypeEnum());
    }

    public enum TypeEnum {
        VOID,
        FUNCTION,
        STRING,
        ARRAY, // 字面数组的值类型
        PTR,
        I1,
        I8,
        I32,
        F32,
        I64,
        F64,
        VEC, // 向量类型 用于 SIMD 操作的向量类型 位宽不固定
        ANY
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
            case I64 -> "i64";
            case F32 -> "float";
            case F64 -> "double";
            case VOID -> "void";
            case STRING -> "str";
            case ARRAY -> "array";
            case FUNCTION -> "func";
            case PTR -> "ptr";
            case VEC -> "vec";
            case ANY -> "any";
        };
    }

    public static IceType fromSysyLiteral(String literal) {
        return switch (literal) {
            case "int" -> I32;
            case "float" -> F32;
            case "double" -> F64;
            case "char" -> I8;
            case "void" -> VOID;
            case "string" -> STRING;
            default -> throw new IllegalArgumentException("Unknown sysY type: " + literal);
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

    public boolean isAny() {
        return this.getTypeEnum() == TypeEnum.ANY;
    }

    public boolean isVector() {
        return this.getTypeEnum() == TypeEnum.VEC;
    }

    public boolean isBoolean() {
        return this.getTypeEnum() == TypeEnum.I1;
    }

    public boolean isInteger() {
        return this.getTypeEnum() == TypeEnum.I32
                || this.getTypeEnum() == TypeEnum.I1
                || this.getTypeEnum() == TypeEnum.I8
                || this.getTypeEnum() == TypeEnum.I64;
    }

    public boolean isFloat() {
        return this.getTypeEnum() == TypeEnum.F32;
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
        return Set.of(TypeEnum.I1, TypeEnum.I8, TypeEnum.I32, TypeEnum.I64, TypeEnum.F32, TypeEnum.F64)
                .contains(this.getTypeEnum());
    }

    public boolean isPointer() {
        return this.getTypeEnum() == TypeEnum.PTR;
    }

    public boolean isConvertibleTo(IceType target) {
        if (target.isAny()) {
            return true; // 任何类型都可以转换为 Any
        }

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

    public int getByteSize() {
        return switch (this.getTypeEnum()) {
            case I1, I8 -> 1;
            case I32, F32 -> 4;
            case I64, F64 -> 8;
            case VOID -> 0;
            case STRING -> 4; // 指针大小
            case FUNCTION -> 4; // 指针大小
            case ARRAY -> 4; // 指针大小
            case PTR -> 4; // 指针大小
            case ANY, VEC -> throw new IllegalStateException("Type does not have a fixed byte size");
        };
    }

    public int getBitSize() {
        return this.getByteSize() * 8;
    }

    public IcePtrType<?> asPointer() {
        return (IcePtrType<?>) this;
    }

    @Override
    public int hashCode() {
        return typeEnum.hashCode();
    }
}
