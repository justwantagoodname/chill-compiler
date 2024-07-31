package top.voidc.frontend.translator;

import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceConstantInt;
import top.voidc.ir.IceFunction;
import top.voidc.ir.IceValue;
import top.voidc.ir.instruction.IceAllocaInstruction;
import top.voidc.ir.type.IceArrayType;
import top.voidc.ir.type.IcePtrType;
import top.voidc.ir.type.IceType;
import top.voidc.misc.Log;
import top.voidc.misc.Tool;

import java.util.ArrayList;

public class FunctionEmitter extends SysyBaseVisitor<IceValue> {
    private int valueCounter = 0;
    private String functionName;
    private IceFunction functionEntity;

    private String generateValueName() {
        return String.valueOf(valueCounter++);
    }

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
                    final var result = arraySizeItem.exp().accept(new ExpEvaluator());
                    Log.should(result instanceof IceConstantInt, "Array size must be constant");
                    arraySize.add((int) ((IceConstantInt) result).getValue());
                }
            }
        }

        var type = IceType.fromSysyLiteral(typeLiteral);
        type = !arraySize.isEmpty() ? IceArrayType.buildNestedArrayType(arraySize, type) : type;
        type = isArray ? new IcePtrType<>(type) : type;
        Log.should(!type.equals(IceType.VOID), "Function parameter cannot be void");
        final var paramValue = new IceAllocaInstruction(generateValueName(), type);
        SymbolTable.current().put(name, paramValue);
        functionEntity.insertInstructionFirst(paramValue);
        return paramValue;
    }

    @Override
    public IceBlock visitBlock(SysyParser.BlockContext ctx) {
        if (!(ctx.parent instanceof SysyParser.FuncDefContext)) {
            SymbolTable.createScope("unnamed:block");
        }

        for (final var blockItem : ctx.blockItem()) {
            blockItem.accept(this);
        }

        if (!(ctx.parent instanceof SysyParser.FuncDefContext)) {
            SymbolTable.exitScope();
        }

        return null;
    }


    private IceValue visitVarArrayDef(SysyParser.VarDefContext ctx) {
        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
        var varType = IceType.fromSysyLiteral(typeLiteral);
        final var name = ctx.Ident().getText();
        final var arraySize = new ArrayList<Integer>();
        for (final var arraySizeItem : ctx.constExp()) {
            final var result = arraySizeItem.accept(new ExpEvaluator());
            Log.should(result instanceof IceConstantInt, "Array size must be constant");
            arraySize.add((int) ((IceConstantInt) result).getValue());
        }
        varType = IceArrayType.buildNestedArrayType(arraySize, varType);
        final var alloca = new IceAllocaInstruction(generateValueName(), varType);
        functionEntity.insertInstructionFirst(alloca);
        SymbolTable.current().put(name, alloca);
        return alloca;
    }

    @Override
    public IceValue visitVarDef(SysyParser.VarDefContext ctx) {
        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
        final var varType = IceType.fromSysyLiteral(typeLiteral);
        final var name = ctx.Ident().getText();

        if (!ctx.constExp().isEmpty()) {
            return visitVarArrayDef(ctx);
        }

        if (ctx.initVal() != null) {
            final var initVal = (IceBlock) ctx.initVal().accept(new ExpEvaluator());
            return null;
        }


        // For non-array type, generate an IceAllocaInstruction
        final var alloca = new IceAllocaInstruction(generateValueName(), varType);
        functionEntity.insertInstructionFirst(alloca);
        SymbolTable.current().put(name, alloca);
        return alloca;
    }

    @Override
    public IceFunction visitFuncDef(SysyParser.FuncDefContext ctx) {
        functionName = ctx.Ident().getText();
        functionEntity = new IceFunction(functionName);
        SymbolTable.createScope(functionName + ":scope");

        final var retTypeLiteral = ctx.funcType().getText();
        final var retType = IceType.fromSysyLiteral(retTypeLiteral);
        functionEntity.setReturnType(retType);

        if (ctx.funcFParams() != null) {
            for (final var param : ctx.funcFParams().funcFParam()) {
                functionEntity.addParameter(param.accept(this));
            }
        }

        this.visitBlock(ctx.block());
        SymbolTable.exitScope();
        return functionEntity;
    }
}
