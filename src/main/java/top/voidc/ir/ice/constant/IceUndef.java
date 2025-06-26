package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceUndef extends IceConstantData {
    public static final IceUndef I32 = new IceUndef(IceType.I32);
    public static final IceUndef F32 = new IceUndef(IceType.F32);
    public static final IceUndef PTR = new IceUndef(new IcePtrType<>(IceType.VOID));

    public static IceUndef get(IceType type) {
        return switch (type.getTypeEnum()) {
            case I32 -> I32;
            case F32 -> F32;
            case PTR -> PTR;
            default -> throw new IllegalArgumentException("Unknown undef type: " + type);
        };
    }

    private IceUndef(IceType type) {
        super(type);
    }

    @Override
    public IceConstantData castTo(IceType type) {
        return switch (type.getTypeEnum()) {
            case I32 -> I32;
            case F32 -> F32;
            case PTR -> PTR;
            default -> throw new IllegalStateException("Unexpected value: " + type);
        };
    }

    @Override
    public IceConstantData clone() {
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return false;
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? type + " " : "") + "undef";
    }

    @Override
    public IceConstantData plus(IceConstantData other) {
        var resultType = other.getType();
        return get(resultType.compareTo(type) > 0 ? resultType : type);
    }

    @Override
    public IceConstantData minus(IceConstantData other) {
        var resultType = other.getType();
        return get(resultType.compareTo(type) > 0 ? resultType : type);
    }

    @Override
    public IceConstantData multiply(IceConstantData other) {
        var resultType = other.getType();
        return get(resultType.compareTo(type) > 0 ? resultType : type);
    }

    @Override
    public IceConstantData divide(IceConstantData other) {
        var resultType = other.getType();
        return get(resultType.compareTo(type) > 0 ? resultType : type);
    }

    @Override
    public IceConstantData mod(IceConstantData other) {
        var resultType = other.getType();
        return get(resultType.compareTo(type) > 0 ? resultType : type);
    }

    @Override
    public IceConstantBoolean lt(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean le(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean gt(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean ge(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean eq(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean ne(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean and(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean or(IceConstantData other) {
        return IceConstantData.create(false);
    }

    @Override
    public IceConstantBoolean not(IceConstantData other) {
        return IceConstantData.create(false);
    }
}
