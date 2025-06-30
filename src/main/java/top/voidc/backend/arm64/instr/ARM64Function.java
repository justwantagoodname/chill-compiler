package top.voidc.backend.arm64.instr;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineRegister;

import java.util.HashMap;
import java.util.Map;

public class ARM64Function extends IceMachineFunction {

    public ARM64Function(String name) {
        super(name);
    }

    // IceValue和存放虚拟寄存器的关系
    private final Map<IceValue, IceMachineRegister> valueToVRegMap = new HashMap<>();



    @Override
    public IceMachineRegister getVirtualRegisterForValue(IceValue value) {
        return null;
    }

    @Override
    public IceMachineRegister getVirtualRegisterForValue(IceValue value, IceType registerType) {
        return null;
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
