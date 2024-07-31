package top.voidc.frontend.translator;

import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceConstantInt;
import top.voidc.ir.IceFunction;
import top.voidc.ir.IceValue;
import top.voidc.ir.type.IceArrayType;
import top.voidc.ir.type.IcePtrType;
import top.voidc.ir.type.IceType;
import top.voidc.misc.Log;

import java.util.ArrayList;

public class FunctionEmitter extends SysyBaseVisitor<IceFunction> {
    private int valueCounter = 0;
    private String functionName;
    private IceFunction functionEntity;

    private String generateValueName() {
        return String.valueOf(valueCounter++);
    }


    private IceValue visitFuncParam(SysyParser.FuncFParamContext ctx) {
        final var typeLiteral = ctx.primitiveType().getText();


        final var name = ctx.Ident().getText();

        final var arraySize = new ArrayList<Integer>();
        boolean isArray = false;
        if (ctx.children.size() > 2) {
            isArray = true;
            if (!ctx.funcFParamArrayItem().isEmpty()) {
                for (final var arraySizeItem: ctx.funcFParamArrayItem()) {
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
        final var paramValue = new IceValue(generateValueName(), type);
        SymbolTable.current().put(name, paramValue);
        return paramValue;
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
            for (final var param: ctx.funcFParams().funcFParam()) {
                functionEntity.addParameter(visitFuncParam(param));
            }
        }

        SymbolTable.exitScope();
        return functionEntity;
    }


}
