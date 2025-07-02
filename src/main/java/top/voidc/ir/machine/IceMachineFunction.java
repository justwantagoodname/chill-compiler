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

    public abstract void bindVirtualRegisterToValue(IceValue value, IceMachineRegister register);

    public abstract void bindPhysicalRegisterToValue(IceValue value, IceMachineRegister register);

    public abstract IceMachineRegister getRegisterForValue(IceValue value);

    public abstract IceMachineRegister allocatePhysicalRegister(String name, IceType type);

    public abstract IceMachineRegister allocateVirtualRegister(String name, IceType type);

    public abstract IceMachineRegister allocateVirtualRegister(IceType type);

    public abstract IceMachineRegister getReturnRegister(IceType type);
}
