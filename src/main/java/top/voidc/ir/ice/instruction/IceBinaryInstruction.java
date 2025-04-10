package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

public class IceBinaryInstruction extends IceInstruction {

    public IceBinaryInstruction(IceBlock parent,
                              InstructionType op,
                              String name,
                              IceType type,
                              IceValue lhs,
                              IceValue rhs) {
        super(parent, name, type);
        setInstructionType(op);
        addOperand(lhs);
        addOperand(rhs);
    }

    public IceBinaryInstruction(IceBlock parent,
                                InstructionType op,
                                IceType type,
                                IceValue lhs,
                                IceValue rhs) {
        super(parent, type);
        setInstructionType(op);
        addOperand(lhs);
        addOperand(rhs);
    }

    public void addOperand(IceValue lhs, IceValue rhs) {
        this.addOperand(lhs);
        this.addOperand(rhs);
    }

    public IceValue getLhs() {
        return getOperand(0);
    }

    public IceValue getRhs() {
        return getOperand(1);
    }

    private void setLhs(IceValue lhs) {
        this.addOperand(lhs);
    }

    private void setRhs(IceValue rhs) {
        this.addOperand(rhs);
    }

    public void addNSW() {
        this.setMetadata("nsw", "true");
    }

    @Override
    public String toString() {
        return getName() + " = " + getInstructionType() + " " + getType() + " " +
                getLhs() + ", " + getRhs();
    }
}
