package top.voidc.ir.machine;

import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

/**
 * 机器寄存器抽象，在寄存器分配后变为真实的物理寄存器
 *
 */
public abstract class IceMachineRegister extends IceUser implements IceArchitectureSpecification {
    private boolean isVirtualize = true;
    private final String asmTemplate;

    public IceMachineRegister(String name, IceType type, String asmTemplate) {
        super(name, type);
        this.asmTemplate = asmTemplate;
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
    public void getTextIR(StringBuilder builder) {
        // TODO: render template
        builder.append(asmTemplate);
    }
}
