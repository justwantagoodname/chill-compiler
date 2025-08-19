package top.voidc.ir.ice.type;


import top.voidc.misc.Flag;

public class IcePtrType <T extends IceType> extends IceType {

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
        if (Boolean.TRUE.equals(Flag.get("-fenable-ptr-type"))) {
            return "ptr";
        } else {
            return pointTo.toString() + "*";
        }
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

    @Override
    public int hashCode() {
        int result = super.hashCode();
        result = 31 * result + (isConst ? 1 : 0);
        result = 31 * result + pointTo.hashCode();
        return result;
    }
}
