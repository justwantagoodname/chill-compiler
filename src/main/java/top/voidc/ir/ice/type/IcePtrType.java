package top.voidc.ir.ice.type;


import top.voidc.ir.IceValue;

import java.util.List;

public class IcePtrType<T extends IceType> extends IceType {

    private boolean isConst = false;
    private final T pointTo;

    public IcePtrType(T pointTo) {
        super(TypeEnum.PTR);
        this.pointTo = pointTo;
    }

    public T getPointTo() {
        return pointTo;
    }

    @Override
    public String toString() {
        return String.format("%s*", pointTo);
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj) && pointTo.equals(((IcePtrType<?>) obj).pointTo);
    }

    public boolean isConst() {
        return isConst;
    }

    public void setConst(boolean aConst) {
        isConst = aConst;
    }
}
