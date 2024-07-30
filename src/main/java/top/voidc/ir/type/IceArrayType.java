package top.voidc.ir.type;

import java.util.List;

public class IceArrayType extends IceType {
    private final IceType elementType;
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

    @Override
    public String toString() {
        return String.format("[%d x %s]", numElements, elementType);
    }
}