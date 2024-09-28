package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;

public class BlockEmitter extends SysyBaseVisitor<IceValue> {
    private final IceContext context;
    public BlockEmitter(IceContext context) {
        this.context = context;
    }


}
