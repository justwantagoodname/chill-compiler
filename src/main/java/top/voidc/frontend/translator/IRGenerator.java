package top.voidc.frontend.translator;


import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceUnit;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;

public class IRGenerator extends SysyBaseVisitor<IceUnit> {
    @Override
    public IceUnit visitCompUnit(SysyParser.CompUnitContext ctx) {
        final var unit = new IceUnit(Flag.get("source"));

        SymbolTable.createScope("global");

        final var globalVariableEmitter = new GlobalDeclEmitter();
        for (var child : ctx.children) {
            if (child instanceof SysyParser.DeclContext) {
                child.accept(globalVariableEmitter);
            }
        }
        Log.d(ctx.children.toString());
        return unit;
    }
}
