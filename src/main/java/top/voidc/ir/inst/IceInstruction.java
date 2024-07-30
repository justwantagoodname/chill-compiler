package top.voidc.ir.inst;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.type.IceType;

public class IceInstruction extends IceUser {
    private final IceBlock block;

    public IceInstruction(String name, IceType iceType, IceBlock block) {
        super(name, iceType);
        this.block = block;
    }

}
