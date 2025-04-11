package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public abstract class IceConstantData extends IceConstant {
    public IceConstantData(String name) {
        super(name, IceType.VOID);
    }

    public abstract IceConstantData castTo(IceType type);

    public abstract IceConstantData clone();
    /**
     * Sysy目前只支持匿名字符串
     */
    public static IceConstantData create(String value) {
        return new IceConstantString(value);
    }

    public static IceConstantData create(String name, double value) {
        return new IceConstantFloat(name, value);
    }

    public static IceConstantData create(String name, long value) {
        return new IceConstantInt(name, value);
    }

    public static IceConstantData create(String name, boolean value) {
        return new IceConstantBoolean(name, value);
    }
}
