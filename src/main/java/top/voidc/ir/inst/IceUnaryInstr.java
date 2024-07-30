package top.voidc.ir.inst;

import top.voidc.ir.IceBlock;
import top.voidc.ir.type.IceType;

public class IceUnaryInstr extends IceInstruction {
    public IceUnaryInstr(String name, IceType iceType, IceBlock block) {
        super(name, iceType, block);
    }
}
