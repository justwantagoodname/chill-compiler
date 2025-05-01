package top.voidc.ir.ice.constant;

import top.voidc.frontend.ir.ConstantVisitor;
import top.voidc.ir.ice.type.IceType;

/**
 * 所有字面常量的基类
 */
public abstract class IceConstantData extends IceConstant {
    public IceConstantData(IceType type) {
        super(null, type);
    }

    public abstract IceConstantData castTo(IceType type);

    public abstract IceConstantData clone();
    /**
     * Sysy目前只支持匿名字符串
     */
    public static IceConstantData create(String value) {
        return IceConstantString.buildString(value);
    }

    public static IceConstantData create(float value) {
        return new IceConstantFloat(value);
    }

    public static IceConstantData create(double value) {return new IceConstantDouble(value);}

    public static IceConstantData create(long value) {
        return new IceConstantInt(value);
    }

    public static IceConstantData create(boolean value) {
        return new IceConstantBoolean(value);
    }

    public static IceConstantData create(char value) {
        return new IceConstantByte((byte) value);
    }

    public static IceConstantData create(byte value) {
        return new IceConstantByte(value);
    }

    public static IceConstant fromTextIR(String textIR) {
        return buildIRParser(textIR).constant().accept(new ConstantVisitor());
    }

    @Override
    abstract public boolean equals(Object o);
}
