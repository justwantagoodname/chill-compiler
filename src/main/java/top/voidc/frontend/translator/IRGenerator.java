package top.voidc.frontend.translator;


import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceUnit;
import top.voidc.misc.Flag;
import top.voidc.misc.Tool;

public class IRGenerator extends SysyBaseVisitor<IceUnit> {
    @Override
    public IceUnit visitCompUnit(SysyParser.CompUnitContext ctx) {
        final var unit = new IceUnit(Flag.get("source"));

        SymbolTable.createScope("global");

        final var globalVariableEmitter = new GlobalDeclEmitter();
        for (var child : ctx.children) {
            if (child instanceof SysyParser.DeclContext) {
                final var globalDecl = child.accept(globalVariableEmitter);
                SymbolTable.current().put(globalDecl.getName(), globalDecl);
                unit.addGlobalDecl(globalDecl);
            }

            if (child instanceof SysyParser.FuncDefContext) {
                final var functionEmitter = new FunctionEmitter();
                final var functionEntity = child.accept(functionEmitter);
                SymbolTable.putFunction(functionEntity.getName(), functionEntity);
                unit.addFunction(functionEntity);
            }
        }

        return unit;
    }
}
