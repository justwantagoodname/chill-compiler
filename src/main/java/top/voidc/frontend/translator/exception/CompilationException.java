package top.voidc.frontend.translator.exception;

import org.antlr.v4.runtime.ParserRuleContext;

import top.voidc.ir.IceContext;

public class CompilationException extends RuntimeException {
    private ParserRuleContext context;
    private IceContext iceContext;

    public CompilationException(String message, ParserRuleContext context, IceContext iceContext) {
        super(message);
        this.context = context;
        this.iceContext = iceContext;
    }

    public ParserRuleContext getContext() {
        return context;
    }

    public IceContext getIceContext() {
        return iceContext;
    }
}
