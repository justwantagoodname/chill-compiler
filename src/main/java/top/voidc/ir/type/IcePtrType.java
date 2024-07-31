package top.voidc.ir.type;


public class IcePtrType<T extends IceType> extends IceType {
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
}
