package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.type.IceType;

public class IceUnreachableInstruction extends IceInstruction {
    public IceUnreachableInstruction(IceBlock parent) {
        super(parent, null, IceType.VOID);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }
}
