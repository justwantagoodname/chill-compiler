package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

public class IceBinaryInstruction extends IceInstruction {

    private IceBinaryInstruction(IceBlock parent, String name, IceType type, InstructionType op) {
        super(parent, name, type);
        setInstructionType(op);
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

//    public static IceBinaryInstruction createAdd(String name, IceValue lhs, IceValue rhs) {
//        final var instr = new IceBinaryInstruction(name, IceType.I32, InstructionType.ADD);
//        instr.setLhs(lhs);
//        instr.setRhs(rhs);
//        return instr;
//    }
//
//    public static IceBinaryInstruction createSub(String name, IceValue lhs, IceValue rhs) {
//        final var instr = new IceBinaryInstruction(name, IceType.I32, InstructionType.SUB);
//        instr.setLhs(lhs);
//        instr.setRhs(rhs);
//        return instr;
//    }
//
//    public static IceBinaryInstruction createMul(String name, IceValue lhs, IceValue rhs) {
//        final var instr = new IceBinaryInstruction(name, IceType.I32, InstructionType.MUL);
//        instr.setLhs(lhs);
//        instr.setRhs(rhs);
//        return instr;
//    }
//
//    public static IceBinaryInstruction createDiv(String name, IceValue lhs, IceValue rhs) {
//        final var instr = new IceBinaryInstruction(name, IceType.I32, InstructionType.DIV);
//        instr.setLhs(lhs);
//        instr.setRhs(rhs);
//        return instr;
//    }

    public void addNSW() {
        this.setMetadata("nsw", "true");
    }
}
