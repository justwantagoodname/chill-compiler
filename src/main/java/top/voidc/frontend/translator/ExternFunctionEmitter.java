package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

import java.util.ArrayList;

public class ExternFunctionEmitter extends FunctionEmitter {

    public IceExternFunction getExternFunction() {
        return externFunction;
    }

    private final IceExternFunction externFunction;

    public ExternFunctionEmitter(IceContext context) {
        super(context);
        this.externFunction = new IceExternFunction(null);
    }

    @Override
    public IceValue visitExternFuncDef(SysyParser.ExternFuncDefContext ctx) {
        String functionName = ctx.Ident().getText();
        externFunction.setName(functionName);

        final var retTypeLiteral = ctx.funcType().getText();
        final var retType = IceType.fromSysyLiteral(retTypeLiteral);
        externFunction.setReturnType(retType);

        // 处理参数声明
        if (ctx.funcPrototypeParams() != null) {

            externFunction.setVArgs(ctx.funcPrototypeParams().varArgs != null);

            for (final var param : ctx.funcPrototypeParams().funcPrototypeParam()) {
                externFunction.addParameter(visitFuncPrototypeParam(param));
            }
        }

        return externFunction;
    }

    @Override
    public IceFunction.IceFunctionParameter visitFuncPrototypeParam(SysyParser.FuncPrototypeParamContext ctx) {
        final var typeLiteral = ctx.primitiveType().getText();
        final var name = ctx.Ident() == null ? null : ctx.Ident().getText();
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

        if (type.isVoid()) {
            throw new CompilationException("Function parameter cannot be void", ctx, context);
        }

        return new IceFunction.IceFunctionParameter(externFunction,
                name == null ? externFunction.generateLocalValueName() : name, type);
    }
}
