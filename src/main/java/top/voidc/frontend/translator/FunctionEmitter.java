package top.voidc.frontend.translator;

import org.antlr.v4.runtime.tree.ParseTree;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.ArrayList;

/**
 * 翻译函数为 IceIR
 * 解析函数体每一条语句，具体由各自的visit方法处理
 */
public class FunctionEmitter extends SysyBaseVisitor<IceValue> {
    private final IceContext context;

    public FunctionEmitter(IceContext context) {
        this.context = context;
    }

    @Override
    public IceValue visit(ParseTree tree) {
        Log.should(tree instanceof SysyParser.FuncDefContext, "Wrong use of the emitter!");
        return super.visit(tree);
    }

    /**
     * 函数声明解析的入口
     * @param ctx the parse tree
     * @return 解析成功的函数对象
     */
    @Override
    public IceFunction visitFuncDef(SysyParser.FuncDefContext ctx) {
        String functionName = ctx.Ident().getText();
        context.setCurrentFunction(new IceFunction(functionName));
        context.getSymbolTable().createScope(functionName + "::scope");

        final var retTypeLiteral = ctx.funcType().getText();
        final var retType = IceType.fromSysyLiteral(retTypeLiteral);
        context.getCurrentFunction().setReturnType(retType);

        // 处理参数声明
        if (ctx.funcFParams() != null) {
            for (final var param : ctx.funcFParams().funcFParam()) {
                context.getCurrentFunction().addParameter(visitFuncFParam(param));
            }
        }

        // 处理函数体

        new CFGEmitter(context, context.getCurrentFunction().getEntryBlock())
                .visitBlock(ctx.block());

        Log.should(context.getSymbolTable().getCurrentScopeName().equals(functionName + "::scope"),
                  "符号表没有被正确还原到顶级作用域");

        context.getSymbolTable().exitScope();
        context.setCurrentFunction(null);
        return context.getCurrentFunction();
    }

    /**
     * 解析函数形参，仅解析一个
     * @param ctx the parse tree
     * @return 生成的函数形参
     */
    @Override
    public IceValue visitFuncFParam(SysyParser.FuncFParamContext ctx) {
        final var typeLiteral = ctx.primitiveType().getText();
        final var name = ctx.Ident().getText();
        final var arraySize = new ArrayList<Integer>();
        boolean isArray = false;
        if (ctx.children.size() > 2) {
            isArray = true;
            if (!ctx.funcFParamArrayItem().isEmpty()) {
                for (final var arraySizeItem : ctx.funcFParamArrayItem()) {
                    final var result = arraySizeItem.exp().accept(new ConstExpEvaluator(context));
                    Log.should(result instanceof IceConstantInt, "Array size must be constant");
                    arraySize.add((int) ((IceConstantInt) result).getValue());
                }
            }
        }

        var type = IceType.fromSysyLiteral(typeLiteral);
        type = !arraySize.isEmpty() ? IceArrayType.buildNestedArrayType(arraySize, type) : type;
        type = isArray ? new IcePtrType<>(type) : type;
        Log.should(!type.equals(IceType.VOID), "Function parameter cannot be void");
        final var paramValue = new IceAllocaInstruction(context.getCurrentFunction().getEntryBlock(), name, type);
        context.getSymbolTable().put(name, paramValue);
        context.getCurrentFunction().getEntryBlock().addInstructionsAtFront(paramValue);
        return paramValue;
    }
}
