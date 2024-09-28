package top.voidc.ir.ice.instruction;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceAllocaInstruction extends IceInstruction {
    public IceAllocaInstruction(String name, IceType type) {
        super(name, new IcePtrType<>(type));
        setInstructionType(InstructionType.ALLOCA);
    }

    @Override
    public String toString() {
        return String.format("%%%s = %s %s", getName(),
                getInstructionType(), ((IcePtrType<?>)getType()).getPointTo());
    }
}
