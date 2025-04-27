package top.voidc.frontend.ir;

import top.voidc.frontend.parser.IceBaseVisitor;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;

public class IceBlockBuilder extends IceBaseVisitor<IceBlock> {

    @Override
    public IceBlock visitBasicBlock(IceParser.BasicBlockContext ctx) {
        return null;
    }
}
