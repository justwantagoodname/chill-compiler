package top.voidc.ir.machine;

import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

/**
 * 机器寄存器抽象，在寄存器分配后变为真实的物理寄存器
 * 默认为虚拟寄存器
 */
public abstract class IceMachineRegister extends IceUser implements IceArchitectureSpecification {
    private boolean isVirtualize = true;

    public IceMachineRegister(String name, IceType type) {
        super(name, type);
    }

    public IceMachineRegister(String name, IceType type, boolean isVirtualize) {
        super(name, type);
        setVirtualize(isVirtualize);
    }

    public int getBitwidth() {
        return getType().getByteSize() * 8;
    }

    public boolean isVirtualize() {
        return isVirtualize;
    }

    public void setVirtualize(boolean virtualize) {
        isVirtualize = virtualize;
    }

    @Override
    public void addOperand(IceValue operand) {
        super.addOperand(operand);
    }

    @Override
    public String getReferenceName(boolean withType) {
        return getName();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getName());
    }

    /**
     * 区分两个机器寄存器的不同是架构和名称
     */
    @Override
    public int hashCode() {
        return (getArchitectureDescription() + getName()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return obj instanceof IceMachineRegister register
                && register.getArchitectureDescription().equals(getArchitectureDescription())
                && register.getName().equals(getName());
    }
}
