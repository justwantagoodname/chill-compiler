package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

public class IceBinaryInstruction extends IceInstruction {

    private boolean isNSW = false;

    public IceBinaryInstruction(IceBlock parent,
                              InstructionType op,
                              String name,
                              IceType type,
                              IceValue lhs,
                              IceValue rhs) {
        super(parent, name, type);
        switch (op) {
            case DIV -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.SDIV);
                    case F32 -> setInstructionType(InstructionType.FDIV);
                }
            }
            case ADD -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.ADD);
                    case F32 -> setInstructionType(InstructionType.FADD);
                }
            }
            case SUB -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.SUB);
                    case F32 -> setInstructionType(InstructionType.SUB);
                }
            }
            case MUL -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.MUL);
                    case F32 -> setInstructionType(InstructionType.FMUL);
                }
            }
            default -> setInstructionType(op);
        }
        addOperand(lhs);
        addOperand(rhs);
    }

    public IceBinaryInstruction(IceBlock parent,
                                InstructionType op,
                                IceType type,
                                IceValue lhs,
                                IceValue rhs) {
        super(parent, type);
        switch (op) {
            case DIV -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.SDIV);
                    case F32 -> setInstructionType(InstructionType.FDIV);
                }
            }
            case ADD -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.ADD);
                    case F32 -> setInstructionType(InstructionType.FADD);
                }
            }
            case SUB -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.SUB);
                    case F32 -> setInstructionType(InstructionType.FSUB);
                }
            }
            case MUL -> {
                switch (lhs.getType().getTypeEnum()) {
                    case I32 -> setInstructionType(InstructionType.MUL);
                    case F32 -> setInstructionType(InstructionType.FMUL);
                }
            }
            default -> setInstructionType(op);
        }
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
        this.isNSW = true;
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = ").append(getInstructionType());
        if (isNSW) {
            builder.append(" nsw");
        }
        builder.append(" ").append(getType()).append(" ")
                .append(getLhs().getReferenceName(false)).append(", ")
                .append(getRhs().getReferenceName(false));
    }

    public boolean isNSW() {
        return isNSW;
    }
}
