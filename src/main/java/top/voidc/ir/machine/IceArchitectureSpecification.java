package top.voidc.ir.machine;

public interface IceArchitectureSpecification {
    default String getArchitectureDescription() {
        return getArchitecture() + "-" + getBitSize() + "-" + getABIName();
    }
    String getArchitecture();
    String getABIName();
    int getBitSize();
}
