package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

public class IceNotInstruction extends IceInstruction {
    public IceNotInstruction(IceBlock parent,
                             String name,
                             IceType type,
                             IceValue operand) {
        super(parent, name, type);
        setInstructionType(IceInstruction.InstructionType.NEG);
        addOperand(operand);
    }

    public IceNotInstruction(IceBlock parent,
                             IceType type,
                             IceValue operand) {
        super(parent, type);
        setInstructionType(IceInstruction.InstructionType.NEG);
        addOperand(operand);
    }
}
