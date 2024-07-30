package top.voidc.ir.inst;

import top.voidc.ir.IceBlock;
import top.voidc.ir.type.IceType;

public class IceBinaryInstr extends IceInstruction {
    public IceBinaryInstr(String name, IceType iceType, IceBlock block) {
        super(name, iceType, block);
    }
}
