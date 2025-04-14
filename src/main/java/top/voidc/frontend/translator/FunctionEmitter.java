package top.voidc.frontend.translator;

import org.antlr.v4.runtime.tree.ParseTree;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.instruction.IceUnreachableInstruction;
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
    protected final IceContext context;

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
        context.getSymbolTable().putFunction(functionName, context.getCurrentFunction());
        context.getSymbolTable().createScope(functionName + "::scope");

        final var retTypeLiteral = ctx.funcType().getText();
        final var retType = IceType.fromSysyLiteral(retTypeLiteral);
        context.getCurrentFunction().setReturnType(retType);

        // 处理参数声明
        if (ctx.funcFParams() != null) {
            for (final var param : ctx.funcFParams().funcFParam()) {
                visitFuncFParam(param);
            }
        }

        // 处理函数体
        final var funcEndBlock = ctx.block().accept(new CFGEmitter(context, context.getCurrentFunction().getEntryBlock()));

        if (!funcEndBlock.equals(context.getCurrentFunction().getExitBlock())
                || funcEndBlock.getSuccessors().isEmpty()
                || funcEndBlock.getInstructions().isEmpty()) {
            // 不是终止块/空块/没有后继，说明没写return
            if (functionName.equals("main")) {
                // main 函数需要返回 0
                funcEndBlock.addInstruction(new IceRetInstruction(funcEndBlock, IceConstantData.create(0)));
            } else if (context.getCurrentFunction().getReturnType().isVoid()
                    && funcEndBlock.getSuccessors().isEmpty()) {
                // 如果函数返回值是void并且没有后继，直接插入返回
                funcEndBlock.addInstruction(new IceRetInstruction(funcEndBlock));
            } else if (funcEndBlock.getInstructions().isEmpty()
                    && funcEndBlock.getSuccessors().isEmpty()) {
                // 是空块并且没有后继 插入 unreachable 后面自动丢弃了
                funcEndBlock.addInstruction(new IceUnreachableInstruction(funcEndBlock));
            } else {
                funcEndBlock.addInstruction(new IceUnreachableInstruction(funcEndBlock));
                Log.w("在 " + funcEndBlock.getName() + " 函数 " + functionName + " 声明了返回值类型，但没有返回值");
            }
        }

        Log.should(context.getSymbolTable().getCurrentScopeName().equals(functionName + "::scope"),
                  "符号表没有被正确还原到顶级作用域");

        context.getSymbolTable().exitScope();
        final var function = context.getCurrentFunction();
        context.setCurrentFunction(null);
        return function;
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
        if (ctx.children.size() > 2) {
            if (!ctx.funcFParamArrayItem().isEmpty()) {
                for (final var arraySizeItem : ctx.funcFParamArrayItem()) {
                    final var result = arraySizeItem.exp().accept(new ConstExpEvaluator(context));
                    arraySize.add((int) ((IceConstantInt) result).getValue());
                }
            }
        }

        var type = IceType.fromSysyLiteral(typeLiteral);
        type = !arraySize.isEmpty() ? IceArrayType.buildNestedArrayType(arraySize, type) : type;
        type = ctx.array != null ? new IcePtrType<>(type) : type;
        Log.should(!type.isVoid(), "Function parameter cannot be void");
        final var parameter = new IceValue(name, type); // 实际的参数

        context.getCurrentFunction().addParameter(parameter);

        final var parameterStackPtr = new IceAllocaInstruction(context.getCurrentFunction().getEntryBlock(),
                name + ".addr",
                type);
        final var store = new IceStoreInstruction(context.getCurrentFunction().getEntryBlock(),
                parameterStackPtr,
                parameter);
        context.getCurrentFunction().getEntryBlock().addInstructionsAtFront(store);

        context.getSymbolTable().put(name, parameterStackPtr);
        context.getCurrentFunction().getEntryBlock().addInstructionsAtFront(parameterStackPtr);
        return parameterStackPtr;
    }
}
