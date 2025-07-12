package top.voidc.backend.arm64.instr;

import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineRegister;

public class ARM64Register extends IceMachineRegister {
    public ARM64Register(String name, IceType type) {
        super(name, type);
    }

    public ARM64Register(String name, IceType type, boolean isVirtualize) {
        super(name, type, isVirtualize);
    }


    @Override
    public RegisterView createView(IceType type) {
        var registerPrefix = switch (type.getTypeEnum()) {
            case I32 -> "w";
            case I64 -> "x";
            case F64 -> "v";
            default -> throw new IllegalStateException();
        };
        return new RegisterView(this, (isVirtualize() ? "virt_" : "") + registerPrefix + getName(), type);
    }

    @Override
    public String getArchitecture() {
        return "armv8-a";
    }

    @Override
    public String getABIName() {
        return "linux-gnu-glibc";
    }

    @Override
    public int getBitSize() {
        return 64;
    }
}
