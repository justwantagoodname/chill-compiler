package top.voidc.ir.ice.constant;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IceType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

/**
 * 代表了一个字面数组常量，但是里面的元素有可能不是常量可以是一个表达式的值
 * 按行优先，每个数组内的元素是压缩存储的，{1, 0, 0, 0, 1} -> [1 x 1, 0 x 3, 1 x 1]
 */

public class IceConstantArray extends IceConstantData {

    public static class DataArrayElement {
        public final IceValue element;
        public final int repeat;

        protected DataArrayElement(IceValue element, int repeat) {
            this.element = element;
            this.repeat = repeat;
        }

        private DataArrayElement(IceValue element) {
            this.element = element;
            this.repeat = 1;
        }

        @Override
        public String toString() {
            if (repeat > 1) {
                assert element instanceof IceConstantInt constantInt && constantInt.getValue() == 0;
                return IntStream.range(0, repeat).mapToObj(i -> element.toString()).collect(Collectors.joining(", "));
            }
            return element.toString();
        }

        public int getRepeat() {
            return repeat;
        }

        public IceValue getElement() {
            return element;
        }
    }

    private List<DataArrayElement> elements;

    private boolean zeroInit = false;

    public IceConstantArray(IceArrayType arrayType, List<DataArrayElement> elements) {
        super(arrayType);
        this.elements = new ArrayList<>(elements);
    }

    public IceConstantArray(IceArrayType arrayType) {
        super(arrayType);
        this.elements = null;
        this.zeroInit = true;
    }

    @Override
    public IceArrayType getType() {
        return (IceArrayType) super.getType();
    }

    @Override
    public IceConstantData castTo(IceType type) {
        if (type instanceof IceArrayType) {
            return this.clone();
        } else {
            throw new IllegalStateException("Unexpected type: " + type);
        }
    }

    @Override
    public IceConstantData clone() {
        if (isZeroInit()) {
            return new IceConstantArray((IceArrayType) getType());
        } else if (elements != null) {
            final var newElements = new ArrayList<DataArrayElement>();
            for (var element : elements) {
                if (element.element instanceof IceConstantData) {
                    newElements.add(new DataArrayElement(((IceConstantData) element.element).clone(), element.repeat));
                } else {
                    newElements.add(new DataArrayElement(element.element, element.repeat));
                }
            }
            return new IceConstantArray((IceArrayType) getType(), newElements);
        }

        return null;
    }

    /**
     * 以完全展开的形式返回数组的元素
     * @apiNote 如果数组比较大，会返回一个很大的数组
     * @return 返回的数组
     */
    public List<IceValue> getFullElements() {
        final var result = new ArrayList<IceValue>();
        if (isZeroInit()) {
            final var type = (IceArrayType) getType();
            for (int i = 0; i < type.getTotalSize(); i++) {
                result.add(new IceConstantInt(0));
            }
        } else {
            assert elements != null;
            elements.forEach(e -> {
                for (int i = 0; i < e.repeat; i++) {
                    if (e.element instanceof IceConstantData) {
                        result.add(((IceConstantData) e.element).clone());
                    } else {
                        result.add(e.element);
                    }
                }
            });
        }
        return result;
    }

    public IceValue get(List<Integer> arrayRef) {
        if (zeroInit) {
            switch (getInsideType().getTypeEnum()) {
                case I1 -> {
                    return new IceConstantBoolean(false);
                }
                case I8, I32 -> {
                    return new IceConstantInt(0);
                }
                case F32 -> {
                    return new IceConstantFloat(0F);
                }

                default -> throw new IllegalStateException("Unexpected value: " + getInsideType().getTypeEnum());
            }
        }
        return get(new ArrayDeque<>(arrayRef));
    }

    private IceValue get(Queue<Integer> arrayRef) {
        // consume the first element
        var first = arrayRef.poll();
        if (first == null) {
            return null;
        }
        int currentIndex = 0;
        assert elements != null;
        for (var elementPair: elements) {
            currentIndex += elementPair.repeat;
            if (currentIndex > first) {
                if (elementPair.element instanceof IceConstantArray) {
                    return ((IceConstantArray) elementPair.element).get(arrayRef);
                } else {
                    if (elementPair.element instanceof IceConstantData) {
                        return ((IceConstantData) elementPair.element).clone();
                    } else {
                        return elementPair.element;
                    }
                }
            }
        }
        return null;
    }

    /**
     * 添加单个元素
     * @param element 要添加的元素
     */
    public void addElement(IceValue element) {
        if (zeroInit) {
            elements = new ArrayList<>();
        }
        assert elements != null;
        elements.add(new DataArrayElement(element));
    }

    public void addElement(IceValue element, int repeat) {
        if (zeroInit) {
            elements = new ArrayList<>();
        }
        assert elements != null;
        elements.add(new DataArrayElement(element, repeat));
    }

    public record ElementRecord(IceValue value, List<Integer> position) {}

    public List<ElementRecord> getNonZeroElements() {
        if (zeroInit) return new ArrayList<>();
        final var result = new ArrayList<ElementRecord>();
        this.getNonZeroElementsImpl(new ArrayList<>(), result);
        return result;
    }

    private void getNonZeroElementsImpl(List<Integer> currentPos, List<ElementRecord> result) {
        if (zeroInit) return;
        assert elements != null;
        for (var i = 0; i < elements.size(); i++) {
            final var e = elements.get(i);
            if (e.element instanceof IceConstantArray) {
                currentPos.add(i);
                ((IceConstantArray) e.element).getNonZeroElementsImpl(currentPos, result);
                currentPos.remove(currentPos.size() - 1);
            } else {
                final var currentIndex = new ArrayList<>(currentPos);
                currentIndex.add(i);
                if (e.element instanceof IceConstantData) {
                    if (e.element instanceof IceConstantInt constantInt) {
                        if (constantInt.getValue() != 0) {
                            result.add(new ElementRecord(
                                    constantInt.clone(),
                                    currentIndex
                            ));
                        }
                    } else if (e.element instanceof IceConstantFloat constantFloat) {
                        if (constantFloat.getValue() != 0) {
                            result.add(new ElementRecord(
                                    constantFloat.clone(),
                                    currentIndex
                            ));
                        }
                    } else {
                        throw new IllegalStateException("Unexpected value: " + e.element);
                    }
                } else {
                    result.add(new ElementRecord(
                            e.element,
                            currentIndex
                    ));
                }
            }
        }
    }

    /**
     * @return 此数组是否由有纯粹的constant组成的
     */
    public boolean isConst() {
        if (zeroInit) return true;
        assert elements != null;
        for (var element : elements) {
            if (element.element instanceof IceConstantArray) {
                if (!((IceConstantArray) element.element).isConst()) {
                    return false;
                }
            } else {
                if (!(element.element instanceof IceConstantData)) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * 这个返回的不对，只是getter方法
     * @see IceConstantArray#isFullZero()
     */
    protected boolean isZeroInit() {
        return zeroInit;
    }

    public void setZeroInit(boolean zeroInit) {
        this.zeroInit = zeroInit;
        if (this.zeroInit) {
            this.elements = null;
        }
    }

    public boolean isFullZero() {
        if (isZeroInit()) return true;
        else {
            return getNonZeroElements().isEmpty();
        }
    }

    @Override
    public String getReferenceName(boolean withType) {
        if (isFullZero()) {
            return (withType ? getType() : "") + " zeroinitializer";
        } else {
            assert elements != null;
            return (withType ? getType() : "")
                    + " ["
                    + String.join(", ", elements.stream().map(DataArrayElement::toString).toList())
                    + "]";
        }
    }

    @Override
    public String getReferenceName() {
        return getReferenceName(true);
    }

    public IceType getInsideType() {
        IceType type = getType();
        while (type instanceof IceArrayType) {
            type = ((IceArrayType) type).getElementType();
        }
        return type;
    }

    // 将当前element最后的未填充的部分填0
    public void fillLastWithZero() {
        if (isZeroInit()) return;
        assert elements != null;
        assert getType().getElementType().isNumeric();
        final int currentElementSize = elements.stream().map(DataArrayElement::getRepeat).reduce(0, Integer::sum);
        if (currentElementSize < getType().getNumElements()) {
            final var elementType = getInsideType();
            final var zeroElement = switch (elementType.getTypeEnum()) {
                case I1 -> new IceConstantBoolean(false);
                case I8, I32 -> new IceConstantInt(0);
                case F32 -> new IceConstantFloat(0F);
                default -> throw new IllegalStateException("Unexpected value: " + elementType.getTypeEnum());
            };
            final var repeat = getType().getNumElements() - currentElementSize;
            addElement(zeroElement, repeat);
        }
    }

    public void fillLastWith(IceValue value) {
        if (isZeroInit()) return;
        assert elements != null;
        final int currentElementSize = elements.stream().map(DataArrayElement::getRepeat).reduce(0, Integer::sum);
        if (currentElementSize < getType().getNumElements()) {
            final var repeat = getType().getNumElements() - currentElementSize;
            addElement(value, repeat);
        }
    }

    public boolean isFull() {
        return elements.stream().map(DataArrayElement::getRepeat).reduce(0, Integer::sum) == getType().getNumElements();
    }

    @Override
    public boolean equals(Object o) {
        throw new UnsupportedOperationException("Not implemented yet: IceConstantArray.equals");
    }
}
