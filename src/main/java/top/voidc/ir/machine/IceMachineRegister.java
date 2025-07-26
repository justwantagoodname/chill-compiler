package top.voidc.ir.machine;

import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;

/**
 * 机器寄存器抽象，在寄存器分配后变为真实的物理寄存器
 * 默认为虚拟寄存器
 */
public abstract class IceMachineRegister extends IceUser implements IceArchitectureSpecification {
    public static class RegisterView extends IceValue implements IceMachineValue {
        private final IceMachineRegister register;

        public RegisterView(IceMachineRegister register, String name, IceType type) {
            super(name, type);
            this.register = register;
        }

        public IceMachineRegister getRegister() {
            return register;
        }

        @Override
        public String getReferenceName(boolean withType) {
            return getName();
        }

        @Override
        public void getTextIR(StringBuilder builder) {
            builder.append(getName());
        }
    }

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

    public abstract RegisterView createView(IceType type);

    @Override
    public String getReferenceName(boolean withType) {
        return (isVirtualize() ? "virt_" : "") + "regslot_" + createView(getType()).getReferenceName();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getName());
    }

    /**
     * 区分两个机器寄存器的不同是架构、类型和名称
     */
    @Override
    public int hashCode() {
        return ((isVirtualize() ? "virt_" : "") + getArchitectureDescription() + getType().getTypeEnum() + getName()).hashCode();
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == this) return true;
        return obj instanceof IceMachineRegister register
                && isVirtualize == register.isVirtualize
                && register.getArchitectureDescription().equals(getArchitectureDescription())
                && register.getType().equals(getType())
                && register.getName().equals(getName());
    }
}
