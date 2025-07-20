package top.voidc.ir.ice.type;

import java.util.List;

/**
 * 数组类型注意和指针类型的区别
 */
public class IceArrayType extends IceType {
    /**
     * 数组类型的元素类型
     */
    private final IceType elementType;
    /**
     * 数组的大小
     */
    private final int numElements;

    public IceArrayType(IceType elementType, int numElements) {
        super(TypeEnum.ARRAY);
        this.elementType = elementType;
        this.numElements = numElements;
    }

    /**
     * 创建一个复合数组类型，arraySize的下标从小到大是从外到内每一维的大小
     * @param arraySize 复合数组的下标，从左到右是从外到内每一维的大小
     * @param elementType 最内层元素类型
     * @return 创建到复合数组类型
     */
    public static IceArrayType buildNestedArrayType(List<Integer> arraySize, IceType elementType) {
        IceType type = elementType;
        for (int i = arraySize.size() - 1; i >= 0; i--) {
            type = new IceArrayType(type, arraySize.get(i));
        }
        return (IceArrayType) type;

    }

    public IceType getElementType() {
        return elementType;
    }

    /**
     * 获取当前维度数组长度
     * @return 数组长度
     */
    public int getNumElements() {
        return numElements;
    }

    public boolean isNested() {
        return getElementType().isArray();
    }

    /**
     * 获取数组的最内层元素类型
     * @return 最内层元素类型
     */
    public IceType getInsideElementType() {
        IceType type = this;
        while (type.isArray()) {
            type = ((IceArrayType) type).getElementType();
        }
        return type;
    }

    /**
     * 获取总维度数
     * @return 获取总维度数
     */
    public int getDimSize() {
        int depth = 0;
        IceType type = this;
        while (type.isArray()) {
            depth++;
            type = ((IceArrayType) type).getElementType();
        }
        return depth;
    }

    /**
     * 获取数组的总大小
     * @return 数组的总大小
     */
    public int getTotalSize() {
        int size = 1;
        IceType type = this;
        while (type.isArray()) {
            size *= ((IceArrayType) type).getNumElements();
            type = ((IceArrayType) type).getElementType();
        }
        return size;
    }

    /**
     * 数组的字节大小是全部的字节大小
     */
    @Override
    public int getByteSize() {
        return getTotalSize() * getInsideElementType().getByteSize();
    }

    @Override
    public String toString() {
        return "[" + numElements + " x " + elementType + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && elementType.equals(((IceArrayType) obj).elementType) && numElements == ((IceArrayType) obj).numElements;
    }
}