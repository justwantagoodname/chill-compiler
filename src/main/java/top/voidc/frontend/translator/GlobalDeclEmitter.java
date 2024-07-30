package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceConstant;
import top.voidc.ir.IceConstantData;
import top.voidc.ir.IceGlobalVariable;
import top.voidc.ir.type.IceType;
import top.voidc.misc.Log;
import top.voidc.misc.Tool;

/**
 * 遍历全局范围中的常量和全局变量
 */

public class GlobalDeclEmitter extends SysyBaseVisitor<IceConstant> {
    @Override
    public IceConstant visitDecl(SysyParser.DeclContext ctx) {
        Log.should(ctx.parent instanceof SysyParser.CompUnitContext, "This can only handle global variable");
        return super.visitDecl(ctx);
    }

    @Override
    public IceConstantData visitConstDef(SysyParser.ConstDefContext ctx) {
        Log.d(ctx.getText());

        final var typeLiteral = ((SysyParser.ConstDeclContext) ctx.parent).primitiveType().getText();
        var constType = IceType.fromSysyLiteral(typeLiteral);

        final boolean isArray = !ctx.constExp().isEmpty();

        if (isArray) {
            Log.e("Array constant is not supported yet");
            Tool.TODO();
            return null;
        }

        final var name = ctx.Ident().getText();

        final var constExp = ctx.initVal().exp();

        final var constValue = constExp.accept(new ExpEvaluator());

        Log.should(constValue instanceof IceConstantData, "Const value should be constant");
        constValue.setName(name);

        return (IceConstantData) constValue;
    }
}
