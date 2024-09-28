package top.voidc.frontend.translator;


import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.IceUnit;
import top.voidc.misc.Flag;

public class IRGenerator extends SysyBaseVisitor<Void> {

    private final IceContext context;

    public IRGenerator(IceContext context) {
        this.context = context;
    }

    public void generateIR() {
        this.visit(context.getAst());
    }

    @Override
    public Void visitCompUnit(SysyParser.CompUnitContext ctx) {
        final var unit = new IceUnit(Flag.get("source"));

        context.getSymbolTable().createScope("global");

        for (var child : ctx.children) {
            if (child instanceof SysyParser.DeclContext) {
                final var globalVariableEmitter = new ConstDeclEmitter(context);
                globalVariableEmitter.emitConstDecl(child).forEach(unit::addGlobalDecl);
            } else if (child instanceof SysyParser.FuncDefContext) {
                final var functionEmitter = new FunctionEmitter(context);
                final var functionEntity = (IceFunction) functionEmitter.visit(child);
                unit.addFunction(functionEntity);
            }
        }
        context.setCurrentIR(unit);
        return null;
    }
}
