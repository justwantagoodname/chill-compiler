package top.voidc.ir.ice.constant;

import top.voidc.frontend.ir.ConstantVisitor;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;

/**
 * 所有字面常量的基类
 */
/**
 * 所有字面常量的基类，提供常量数据的基本操作和运算接口
 * 包括类型转换、算术运算、比较运算和逻辑运算
 */
public abstract class IceConstantData extends IceConstant implements IceMachineValue {
    public IceConstantData(IceType type) {
        super(null, type);
    }

    /**
     * 将当前常量转换为指定类型
     * @param type 目标类型
     * @return 转换后的常量
     * @throws IllegalStateException 如果无法转换到目标类型
     */
    public abstract IceConstantData castTo(IceType type);

    public abstract IceConstantData clone();
    /**
     * Sysy目前只支持匿名字符串
     */
    public static IceConstantString create(String value) {
        return IceConstantString.buildString(value);
    }

    public static IceConstantFloat create(float value) {
        return new IceConstantFloat(value);
    }

    public static IceConstantDouble create(double value) {return new IceConstantDouble(value);}

    public static IceConstantInt create(long value) {
        return new IceConstantInt(value);
    }

    public static IceConstantBoolean create(boolean value) {
        return new IceConstantBoolean(value);
    }

    public static IceConstantByte create(char value) {
        return new IceConstantByte((byte) value);
    }

    public static IceConstantByte create(byte value) {
        return new IceConstantByte(value);
    }

    public static IceConstant fromTextIR(String textIR) {
        return buildIRParser(textIR).constant().accept(new ConstantVisitor());
    }

    @Override
    abstract public boolean equals(Object o);

    /**
     * 加法运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持加法运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantData plus(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 减法运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持减法运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantData minus(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 乘法运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持乘法运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantData multiply(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 除法运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持除法运算
     * @throws NullPointerException 如果参数为null
     * @throws ArithmeticException 如果除数为0
     */
    public IceConstantData divide(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 取模运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持取模运算
     * @throws NullPointerException 如果参数为null
     * @throws ArithmeticException 如果除数为0
     */
    public IceConstantData mod(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 小于比较
     * @param other 另一个操作数
     * @return 比较结果
     * @throws UnsupportedOperationException 如果该类型不支持比较运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean lt(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 小于等于比较
     * @param other 另一个操作数
     * @return 比较结果
     * @throws UnsupportedOperationException 如果该类型不支持比较运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean le(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 大于比较
     * @param other 另一个操作数
     * @return 比较结果
     * @throws UnsupportedOperationException 如果该类型不支持比较运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean gt(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 大于等于比较
     * @param other 另一个操作数
     * @return 比较结果
     * @throws UnsupportedOperationException 如果该类型不支持比较运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean ge(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 等于比较
     * @param other 另一个操作数
     * @return 比较结果
     * @throws UnsupportedOperationException 如果该类型不支持比较运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean eq(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 不等于比较
     * @param other 另一个操作数
     * @return 比较结果
     * @throws UnsupportedOperationException 如果该类型不支持比较运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean ne(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 逻辑与运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持逻辑运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean and(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 逻辑或运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持逻辑运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean or(IceConstantData other) {
        throw new UnsupportedOperationException();
    }

    /**
     * 逻辑非运算
     * @param other 另一个操作数
     * @return 运算结果
     * @throws UnsupportedOperationException 如果该类型不支持逻辑运算
     * @throws NullPointerException 如果参数为null
     */
    public IceConstantBoolean not(IceConstantData other) {
        throw new UnsupportedOperationException();
    }
}
