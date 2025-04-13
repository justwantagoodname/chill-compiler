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

    public int getNumElements() {
        return numElements;
    }

    public boolean isNested() {
        return getElementType().isArray();
    }

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

    @Override
    public String toString() {
        return "[" + numElements + " x " + elementType + "]";
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && elementType.equals(((IceArrayType) obj).elementType) && numElements == ((IceArrayType) obj).numElements;
    }
}