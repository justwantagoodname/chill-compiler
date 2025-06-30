package top.voidc.ir.machine;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceType;

public abstract class MachineInstruction extends IceInstruction {

    public MachineInstruction(IceBlock parent, String name, IceType type) {
        super(parent, name, type);
    }
}
