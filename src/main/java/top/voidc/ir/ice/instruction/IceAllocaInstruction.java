package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceAllocaInstruction extends IceInstruction {
    public IceAllocaInstruction(IceBlock parent, String name, IceType type) {
        super(parent, name, new IcePtrType<>(type));
        setInstructionType(InstructionType.ALLOCA);
    }

    @Override
    public String toString() {
        return "%" + getName() + " = " + getInstructionType() + " " + ((IcePtrType<?>) getType()).getPointTo();
    }
}
