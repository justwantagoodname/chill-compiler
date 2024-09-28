package top.voidc.frontend.translator;

import org.antlr.v4.runtime.tree.ParseTree;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceBlock;
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
 * 翻译函数为 IceIR 会生成一整个 block 后续进行基本块分割
 */
public class FunctionEmitter extends SysyBaseVisitor<IceValue> {
    private IceFunction functionEntity;
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
        functionEntity = new IceFunction(functionName);
        context.getSymbolTable().createScope(functionName + ":scope");

        final var retTypeLiteral = ctx.funcType().getText();
        final var retType = IceType.fromSysyLiteral(retTypeLiteral);
        functionEntity.setReturnType(retType);

        // 处理参数声明
        if (ctx.funcFParams() != null) {
            for (final var param : ctx.funcFParams().funcFParam()) {
                functionEntity.addParameter(visitFuncFParam(param));
            }
        }

        this.visitBlock(ctx.block());
        context.getSymbolTable().exitScope();
        return functionEntity;
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
        final var paramValue = new IceAllocaInstruction(functionEntity.generateLocalValueName(), type);
        context.getSymbolTable().put(name, paramValue);
        functionEntity.insertInstructionFirst(paramValue);
        return paramValue;
    }

    @Override
    public IceBlock visitBlock(SysyParser.BlockContext ctx) {
        if (!(ctx.parent instanceof SysyParser.FuncDefContext)) {
            context.getSymbolTable().createScope("unnamed:block");
        }

        for (final var blockItem : ctx.blockItem()) {
            blockItem.accept(this);
        }

        if (!(ctx.parent instanceof SysyParser.FuncDefContext)) {
            context.getSymbolTable().exitScope();
        }

        return null;
    }

//    private IceValue visitVarArrayDef(SysyParser.VarDefContext ctx) {
//        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
//        var varType = IceType.fromSysyLiteral(typeLiteral);
//        final var name = ctx.Ident().getText();
//        final var arraySize = new ArrayList<Integer>();
//        for (final var arraySizeItem : ctx.constExp()) {
//            final var result = arraySizeItem.accept(new ConstExpEvaluator());
//            Log.should(result instanceof IceConstantInt, "Array size must be constant");
//            arraySize.add((int) ((IceConstantInt) result).getValue());
//        }
//        varType = IceArrayType.buildNestedArrayType(arraySize, varType);
//        final var alloca = new IceAllocaInstruction(functionEntity.generateLocalValueName(), varType);
//        functionEntity.insertInstructionFirst(alloca);
//        SymbolTable.current().put(name, alloca);
//        return alloca;
//    }
//
//    @Override
//    public IceValue visitVarDef(SysyParser.VarDefContext ctx) {
//        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
//        final var varType = IceType.fromSysyLiteral(typeLiteral);
//        final var name = ctx.Ident().getText();
//
//        if (!ctx.constExp().isEmpty()) {
//            return visitVarArrayDef(ctx);
//        }
//
//        if (ctx.initVal() != null) {
//            final var initVal = (IceBlock) ctx.initVal().accept(new ConstExpEvaluator());
//            return null;
//        }
//
//
//        // For non-array type, generate an IceAllocaInstruction
//        final var alloca = new IceAllocaInstruction(functionEntity.generateLocalValueName(), varType);
//        functionEntity.insertInstructionFirst(alloca);
//        SymbolTable.current().put(name, alloca);
//        return alloca;
//    }
}
