package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public class IceUndef extends IceConstant {
    public static final IceUndef I32 = new IceUndef(IceType.I32);
    public static final IceUndef F32 = new IceUndef(IceType.F32);

    public static IceUndef get(IceType type) {
        return switch (type.getTypeEnum()) {
            case I32 -> I32;
            case F32 -> F32;
            default -> throw new IllegalArgumentException("Unknown undef type: " + type);
        };
    }

    private IceUndef(IceType type) {
        super("undef", type);
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? type : "") + " undef";
    }
}
