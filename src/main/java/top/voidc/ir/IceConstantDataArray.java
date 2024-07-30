package top.voidc.ir;

import top.voidc.ir.type.IceArrayType;
import top.voidc.ir.type.IceType;
import top.voidc.misc.Tool;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 *
 */

public class IceConstantDataArray extends IceConstantData {

    public static class DataArrayElement {
        public final IceConstantData element;
        public final int repeat;

        private DataArrayElement(IceConstantData element, int repeat) {
            this.element = element;
            this.repeat = repeat;
        }

        private DataArrayElement(IceConstantData element) {
            this.element = element;
            this.repeat = 1;
        }

        @Override
        public String toString() {
            if (repeat > 1) {
                return String.format("(%s x %d)", element, repeat);
            }
            return String.format("%s", element, repeat);
        }
    }

    private final List<DataArrayElement> elements;
    private boolean zeroInit = false;

    public IceConstantDataArray(String name, IceArrayType arrayType, List<DataArrayElement> elements) {
        super(name);
        setType(arrayType);
        this.elements = elements;
    }

    public IceConstantDataArray(String name, IceArrayType arrayType) {
        super(name);
        setType(arrayType);
        this.elements = null;
        this.zeroInit = true;
    }

    @Override
    public IceConstantData castTo(IceType type) {
        Tool.TODO();
        return null;

    }

    public List<IceConstantData> getElements() {
        final var result = new ArrayList<IceConstantData>();
        elements.forEach(e -> {
            for (int i = 0; i < e.repeat; i++) {
                result.add(e.element);
            }
        });
        return result;
    }

    public IceConstantData get(int index) {
        return elements.get(index).element;
    }

    public IceConstantData get(List<Integer> arrayRef) {
        return get(arrayRef.stream());
    }


    private IceConstantData get(Stream<Integer> arrayRef) {
        // consume the first element
        var first = arrayRef.findFirst();
        if (first.isEmpty()) {
            return null;
        }
        int currentIndex = 0;
        for (var elementPair: elements) {
            currentIndex += elementPair.repeat;
            if (currentIndex > first.get()) {
                if (elementPair.element instanceof IceConstantDataArray) {
                    return ((IceConstantDataArray) elementPair.element).get(arrayRef);
                } else {
                    return elementPair.element;
                }
            }
        }
        return null;
    }

    public void addElement(IceConstantData element) {
        elements.add(new DataArrayElement(element));
    }

    public void addElement(IceConstantData element, int repeat) {
        elements.add(new DataArrayElement(element, repeat));
    }

    @Override
    public String toString() {
        if (zeroInit) {
            return "zeroinitializer";
        }
        if (getName() != null) return String.format("@%s = [%s]", getName(), String.join(", ", elements.stream().map(DataArrayElement::toString).toList()));
        else return String.format("[%s]", String.join(", ", elements.stream().map(DataArrayElement::toString).toList()));
    }

    public IceType getInsideType() {
        IceType type = getType();
        while (type instanceof IceArrayType) {
            type = ((IceArrayType) type).getElementType();
        }
        return type;
    }
}
