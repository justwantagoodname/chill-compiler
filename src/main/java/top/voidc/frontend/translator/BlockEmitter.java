package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;

public class BlockEmitter extends SysyBaseVisitor<IceValue> {
    private final IceContext context;
    private final IceFunction function;

    public BlockEmitter(IceContext context, IceFunction function) {
        this.context = context;
        this.function = function;
    }

    @Override
    public IceValue visitBlock(SysyParser.BlockContext ctx) {
        final var isAnonBlock = !(ctx.parent instanceof SysyParser.FuncDefContext);
        if (isAnonBlock) {
            context.getSymbolTable().createScope("unnamed:block");
        }

        for (final var blockItem : ctx.blockItem()) {
            blockItem.accept(this);
        }

        if (isAnonBlock) {
            context.getSymbolTable().exitScope();
        }

        return null;
    }
}
