package top.voidc.ir.machine;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;

import java.util.*;

/**
 *
 */
public abstract class IceMachineFunction extends IceFunction implements IceArchitectureSpecification {
    private boolean hasCall = false; // 是否有外部调用

    public IceMachineFunction(String name) {
        super(name);
    }

    public abstract List<IceStackSlot> getStackFrame();

    public abstract IceStackSlot allocateStackSlot(IceType type, IceStackSlot.StackSlotType stackSlotType);

    public abstract void bindMachineValueToValue(IceValue value, IceMachineValue machineValue);

    public abstract void bindVirtualRegisterToValue(IceValue value, IceMachineRegister.RegisterView register);

    public abstract void bindPhysicalRegisterToValue(IceValue value, IceMachineRegister.RegisterView register);

    public abstract Optional<IceMachineValue> getRegisterForValue(IceValue value);

    /**
     * 给 MachineFunction 分配物理寄存器单元，仅供寄存器分配器使用
     */
    protected abstract IceMachineRegister allocatePhysicalRegister(String name, IceType type);

    /**
     * 给 MachineFunction 分配虚拟寄存器单元
     */
    protected abstract IceMachineRegister allocateVirtualRegister(String name, IceType type);

    public abstract IceMachineRegister.RegisterView allocateVirtualRegister(IceType type);

    public abstract IceMachineRegister getPhysicalRegister(String name);

    public abstract IceMachineRegister.RegisterView getReturnRegister(IceType type);

    public abstract IceMachineRegister.RegisterView getZeroRegister(IceType type);

    public abstract Set<IceMachineRegister> getAllRegisters();

    /**
     * 获取当前函数是否有调用外部函数
     * @return 如果有调用外部函数则返回true，否则返回false
     */
    public boolean isHasCall() {
        return hasCall;
    }

    /**
     * 设置当前函数是否有调用外部函数
     * @param hasCall 如果有调用外部函数则设置为true，否则设置为false
     */
    public void setHasCall(boolean hasCall) {
        this.hasCall = hasCall;
    }

    /**
     * 获取实际汇编中的基本块入口
     * @param name 原函数中的基本块名称
     * @return 对应的机器指令块
     */
    public abstract IceMachineBlock getMachineBlock(String name);

    @Override
    public String getReferenceName(boolean withType) {
        return getName();
    }
}
