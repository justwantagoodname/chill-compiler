package top.voidc.ir.ice.constant;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IceType;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

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
                assert element.equals(IceConstantData.create(0));
                return "[" + element + " x " + repeat + "] zeroinitializer";
            }
            return element.toString();
        }
    }

    private final List<DataArrayElement> elements;
    private boolean zeroInit = false;
    private boolean isPrivate = false;
    private boolean isUnnamedAddr = false;

    public IceConstantArray(IceArrayType arrayType, List<DataArrayElement> elements) {
        super(arrayType);
        this.elements = elements;
    }

    public IceConstantArray(IceArrayType arrayType) {
        super(arrayType);
        this.elements = null;
        this.zeroInit = true;
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
        if (zeroInit) {
            final var type = (IceArrayType) getType();
            for (int i = 0; i < type.getTotalSize(); i++) {
                result.add(new IceConstantInt(0));
            }
            return result;
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
            return result;
        }
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

    public void addElement(IceValue element) {
        if (zeroInit) {
            throw new IllegalStateException("Cannot add element to zero initialized array");
        }
        assert elements != null;
        elements.add(new DataArrayElement(element));
    }

    public void addElement(IceValue element, int repeat) {
        if (zeroInit) {
            throw new IllegalStateException("Cannot add element to zero initialized array");
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
        assert elements != null;
        for (var i = 0; i < result.size(); i++) {
            final var e = elements.get(i);
            if (e.element instanceof IceConstantArray) {
                currentPos.add(i);
                ((IceConstantArray) e.element).getNonZeroElementsImpl(currentPos, result);
                currentPos.remove(currentPos.size() - 1);
            } else {
                final var currentIndex = new ArrayList<>(currentPos);
                currentIndex.add(i);
                if (e.element instanceof IceConstantData) {
                    if (e.element instanceof IceConstantInt constantInt && constantInt.getValue() != 0) {;
                        result.add(new ElementRecord(
                                constantInt.clone(),
                                currentIndex
                        ));
                    } else if (e.element instanceof IceConstantFloat constantFloat && constantFloat.getValue() != 0) {
                        result.add(new ElementRecord(
                                constantFloat.clone(),
                                currentIndex
                        ));
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

    public boolean isZeroInit() {
        return zeroInit;
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public boolean isUnnamedAddr() {
        return isUnnamedAddr;
    }

    public void setUnnamedAddr(boolean unnamedAddr) {
        isUnnamedAddr = unnamedAddr;
    }


    @Override
    public String getReferenceName(boolean withType) {
        if (zeroInit) {
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
}
