package top.voidc.ir.ice.interfaces;

public interface IceArchitectureSpecification {
    default String getArchitectureDescription() {
        return getArchitecture() + "-" + getBitSize() + "-" + getABIName();
    }
    String getArchitecture();
    String getABIName();
    int getBitSize();
}
