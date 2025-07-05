package top.voidc.ir.machine;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceType;

import java.util.*;

/**
 *
 */
public abstract class IceMachineFunction extends IceFunction implements IceArchitectureSpecification {
    public IceMachineFunction(String name) {
        super(name);
    }

    public abstract void bindVirtualRegisterToValue(IceValue value, IceMachineRegister register);

    public abstract void bindPhysicalRegisterToValue(IceValue value, IceMachineRegister register);

    public abstract Optional<IceMachineRegister> getRegisterForValue(IceValue value);

    public abstract IceMachineRegister allocatePhysicalRegister(String name, IceType type);

    public abstract IceMachineRegister allocateVirtualRegister(String name, IceType type);

    public abstract IceMachineRegister allocateVirtualRegister(IceType type);

    public abstract IceMachineRegister getReturnRegister(IceType type);

    /**
     * 获取实际汇编中的基本块入口
     * @param name 原函数中的基本块名称
     * @return 对应的机器指令块
     */
    public abstract IceBlock getMachineBlock(String name);

    public abstract Collection<IceBlock> getMachineBlocks();

    @Override
    public String getReferenceName(boolean withType) {
        return getName();
    }
}
