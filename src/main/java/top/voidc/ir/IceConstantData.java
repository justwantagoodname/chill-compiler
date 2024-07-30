package top.voidc.ir;

import static top.voidc.misc.Tool.TODO;

public class IceConstantData extends IceConstant {
    public IceConstantData(String name) {
        super(name);
    }

    public static IceConstantData create(String name, long value) {
        return new IceConstantInt(name, value);
    }

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
