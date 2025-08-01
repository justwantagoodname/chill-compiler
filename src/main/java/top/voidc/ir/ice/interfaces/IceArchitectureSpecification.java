package top.voidc.ir.ice.interfaces;

public interface IceArchitectureSpecification {
    default String getArchitectureDescription() {
        return getArchitecture() + "-" + getArchitectureBitSize() + "-" + getABIName();
    }
    String getArchitecture();
    String getABIName();
    int getArchitectureBitSize();
}
