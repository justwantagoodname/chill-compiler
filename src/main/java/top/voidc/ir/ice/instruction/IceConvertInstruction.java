package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

public class IceConvertInstruction extends IceInstruction {
    public IceConvertInstruction(IceBlock parent,
                                 String name,
                                 IceType type,
                                 IceValue operand) {
        super(parent, name, type);
        setInstructionType(InstructionType.TCONVERT);
        addOperand(operand);
    }

    public IceConvertInstruction(IceBlock parent,
                                 IceType type,
                                 IceValue operand) {
        super(parent, type);
        setInstructionType(InstructionType.TCONVERT);
        addOperand(operand);
    }
}
