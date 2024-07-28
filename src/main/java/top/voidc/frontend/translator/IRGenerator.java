package top.voidc.frontend.translator;


import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;

public class IRGenerator extends SysyBaseVisitor<Void> {
    @Override
    public Void visitCompUnit(SysyParser.CompUnitContext ctx) {
        System.out.println("visitCompUnit");
        System.out.println(ctx.getText());
        return super.visitCompUnit(ctx);
    }
}
