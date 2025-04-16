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

    public IceLoadInstruction(IceBlock parent, IceValue source) {
        super(parent, ((IcePtrType<?>)source.getType()).getPointTo());
        setInstructionType(InstructionType.LOAD);
        this.addOperand(source);
    }

    public IceValue getSource() {
        return getOperand(0);
    }

    public void setSource(IceValue source) {
        setOperand(0, source);
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = ").append(getInstructionType()).append(" ")
                .append(getType()).append(", ").append(getSource().getReferenceName());
    }
}
