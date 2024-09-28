package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.IceValue;

public class IceLoadInstruction extends IceInstruction {

    public IceLoadInstruction(IceBlock parent, String name, IceValue source) {
        super(parent, name, ((IcePtrType<?>)source.getType()).getPointTo());
        setInstructionType(InstructionType.LOAD);
        this.addOperand(source);
    }

    public IceValue getSource() {
        return getOperand(0);
    }

    @Override
    public String toString() {
        return String.format("%%%s = load %s, %%%s", getName(), getSource().getType(), getSource().getName());
    }
}