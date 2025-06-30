package top.voidc.ir.machine;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceType;

import java.util.HashMap;
import java.util.Map;

/**
 *
 */
public abstract class IceMachineFunction extends IceFunction implements IceArchitectureSpecification {
    public IceMachineFunction(String name) {
        super(name);
    }

    private final Map<IceValue, IceMachineRegister> valueToVRegMap = new HashMap<>();

    public abstract IceMachineRegister getVirtualRegisterForValue(IceValue value);

    public abstract IceMachineRegister getVirtualRegisterForValue(IceValue value, IceType registerType);
}
