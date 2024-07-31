package top.voidc.ir;

import top.voidc.ir.type.IceType;

import static top.voidc.misc.Tool.TODO;

public abstract class IceConstantData extends IceConstant {
    public IceConstantData(String name) {
        super(name, IceType.VOID);
    }

    public static IceConstantData create(String name, long value) {
        return new IceConstantInt(name, value);
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
}
