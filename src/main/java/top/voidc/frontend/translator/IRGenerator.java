package top.voidc.frontend.translator;


import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.IceUnit;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;

public class IRGenerator extends SysyBaseVisitor<Void> {

    private final IceContext context;

    public IRGenerator(IceContext context) {
        this.context = context;
    }

    public void generateIR() {
//        try {
            this.visit(context.getAst());
//        } catch (CompilationException e) {
//            handleCompilationException(e);
//        }
    }

    public void handleCompilationException(CompilationException e) {
        Log.e("编译错误: " + e.getMessage());
        Log.e("在 " + Flag.get("source") +
                " 的 " + e.getContext().getStart().getLine() +
                " 行 " + e.getContext().getStart().getCharPositionInLine() + " 列");
        Log.e("Code: " + e.getContext().getText());
        System.exit(1);
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
                context.getSymbolTable().putFunction(functionEntity.getName(), functionEntity);
                unit.addFunction(functionEntity);
            } else if (child instanceof SysyParser.ExternFuncDefContext externFuncDefContext) {
                final var externFunctionEmitter = new ExternFunctionEmitter(context);
                externFuncDefContext.accept(externFunctionEmitter);
                unit.addFunction(externFunctionEmitter.getExternFunction());
            }
        }
        context.setCurrentIR(unit);
        return null;
    }
}
