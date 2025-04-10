package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

public class IceNegInstruction extends IceInstruction {

    public IceNegInstruction(IceBlock parent,
                             String name,
                             IceType type,
                             IceValue operand) {
        super(parent, name, type);
        setInstructionType(InstructionType.NEG);
        addOperand(operand);
    }

    public IceNegInstruction(IceBlock parent,
                             IceType type,
                             IceValue operand) {
        super(parent, type);
        setInstructionType(InstructionType.NEG);
        addOperand(operand);
    }
}
