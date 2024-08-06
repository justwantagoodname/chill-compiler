package top.voidc.ir.instruction;

import top.voidc.ir.type.IcePtrType;
import top.voidc.ir.IceValue;

public class IceLoadInstruction extends IceInstruction {
    private final IceValue source;

    public IceLoadInstruction(String name, IceValue source) {
        super(name, ((IcePtrType<?>)source.getType()).getPointTo());
        setInstructionType(InstructionType.LOAD);
        this.source = source;

        this.addOperand(source);
    }

    public IceValue getSource() {
        return source;
    }

    @Override
    public String toString() {
        return String.format("%%%s = load %s, %%%s", getName(), source.getType(), source.getName());
    }
}