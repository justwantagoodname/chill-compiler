package top.voidc.frontend.translator.exception;

import org.antlr.v4.runtime.ParserRuleContext;
import top.voidc.ir.IceContext;

public class EvaluationException extends CompilationException {
    public EvaluationException(ParserRuleContext context, IceContext iceContext) {
        super("表达式不是编译期常量", context, iceContext);
    }
}
