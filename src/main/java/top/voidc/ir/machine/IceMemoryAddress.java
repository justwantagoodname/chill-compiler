package top.voidc.ir.machine;

public interface IceMemoryAddress {
    boolean isStackAddress();
    boolean isStaticAddress();
    default boolean isReadonly() {
        return false;
    }
}
