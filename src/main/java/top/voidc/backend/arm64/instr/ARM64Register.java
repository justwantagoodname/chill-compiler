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
    public String getArchitecture() {
        return "aarch64";
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
