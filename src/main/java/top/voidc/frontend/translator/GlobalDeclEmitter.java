package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.IceConstant;
import top.voidc.ir.IceConstantData;
import top.voidc.ir.IceConstantDataArray;
import top.voidc.ir.IceConstantInt;
import top.voidc.ir.type.IceArrayType;
import top.voidc.ir.type.IceType;
import top.voidc.misc.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;

/**
 * 遍历全局范围中的常量和全局变量
 */

public class GlobalDeclEmitter extends SysyBaseVisitor<IceConstant> {
    private final Set<SysyParser.InitValContext> visited = new HashSet<>();

    @Override
    protected IceConstant aggregateResult(IceConstant aggregate, IceConstant nextResult) {
        if (aggregate == null) {
            return nextResult;
        }
        return aggregate;
    }

    @Override
    public IceConstant visitDecl(SysyParser.DeclContext ctx) {
        Log.should(ctx.parent instanceof SysyParser.CompUnitContext, "This can only handle global variable");
        return super.visitDecl(ctx);
    }

    private void fillArray(IceConstantDataArray arrayDecl, SysyParser.InitValContext initVal, ArrayList<Integer> arraySize, int depth) {
        Log.should(depth < arraySize.size(), "Array size should be consistent with dimension");

        var currentArraySize = arraySize.get(depth); // 当前类型(维度)下剩余填充需要的元素数量

        for (final var initValItem : initVal.initVal()) {
            if (visited.contains(initValItem)) continue;
            if (currentArraySize == 0) return; // 当前维度已经填充完毕，直接返回填充下一个维度

            if (initValItem.initVal().isEmpty() && initValItem.exp() != null) {
                if (arraySize.get(depth + 1) == 1) {
                    // 内部是一个表达式，这个表达式应该是一个常量表达式
                    var constValue = initValItem.exp().accept(new ExpEvaluator());
                    Log.should(constValue instanceof IceConstantData, "Const value should be constant");
                    // 最后一维度，直接添加到数组中
                    if (!constValue.getType().equals(arrayDecl.getInsideType())) {
                        constValue = ((IceConstantData) constValue).castTo(arrayDecl.getInsideType());
                    }
                    arrayDecl.addElement((IceConstantData) constValue);
                    currentArraySize--;
                } else {
                    // 还有下一维度，递归填充
                    final var subArrayType = ((IceArrayType) arrayDecl.getType()).getElementType();
                    final var nestedArray = new IceConstantDataArray(null, (IceArrayType) subArrayType, new ArrayList<>());
                    fillArray(nestedArray, initVal, arraySize, depth + 1);
                    arrayDecl.addElement(nestedArray);
                    currentArraySize--;
                }
            } else {
                // 内部是一个数组，递归填充
                final var subArrayType = ((IceArrayType) arrayDecl.getType()).getElementType();
                final var nestedArray = new IceConstantDataArray(null, (IceArrayType) subArrayType, new ArrayList<>());
                fillArray(nestedArray, initValItem, arraySize, depth + 1);
                arrayDecl.addElement(nestedArray);
                currentArraySize--;
            }
            visited.add(initValItem);
        }
        if (currentArraySize > 0) {
            // 内部是一个数组，递归填充
            final var subArrayType = ((IceArrayType) arrayDecl.getType()).getElementType();
            if (subArrayType instanceof IceArrayType) {
                final var nestedArray = new IceConstantDataArray(null, (IceArrayType) subArrayType);
                arrayDecl.addElement(nestedArray);
            } else {
                switch (arrayDecl.getInsideType().getTypeEnum()) {
                    case I32 -> arrayDecl.addElement(IceConstantData.create(null, 0), currentArraySize);
                    case F32 -> arrayDecl.addElement(IceConstantData.create(null, 0.0), currentArraySize);
                    default -> throw new IllegalStateException("Unexpected value: " + arrayDecl.getType());
                }
            }
        }
    }

    public IceConstantDataArray visitConstArrayDef(SysyParser.ConstDefContext ctx) {
        final var typeLiteral = ((SysyParser.ConstDeclContext) ctx.parent).primitiveType().getText();
        final var elementType = IceType.fromSysyLiteral(typeLiteral);
        final var name = ctx.Ident().getText();
        final var arraySize = new ArrayList<Integer>();

        for (var exp : ctx.constExp()) {
            var constValue = exp.accept(new ExpEvaluator());
            Log.should(constValue instanceof IceConstantInt, "Const value should be constant");
            arraySize.add((int) ((IceConstantInt) constValue).getValue());
        }
        final var arrayType = IceArrayType.buildNestedArrayType(arraySize, elementType);

        final var arrayDecl = new IceConstantDataArray(name, arrayType, new ArrayList<>());

        // compute dimension size
        arraySize.add(1);

        visited.clear();
        fillArray(arrayDecl, ctx.initVal(), arraySize, 0);
        visited.clear();

        arrayDecl.setName(name);

        return arrayDecl;
    }

    @Override
    public IceConstantData visitConstDef(SysyParser.ConstDefContext ctx) {
        final boolean isArray = !ctx.constExp().isEmpty();

        if (isArray) {
            return visitConstArrayDef(ctx);
        }

        final var typeLiteral = ((SysyParser.ConstDeclContext) ctx.parent).primitiveType().getText();
        var constType = IceType.fromSysyLiteral(typeLiteral);

        final var name = ctx.Ident().getText();

        final var constExp = ctx.initVal().exp();

        var constValue = constExp.accept(new ExpEvaluator());

        Log.should(constValue instanceof IceConstantData, "Const value should be constant");
        if (constType != constValue.getType()) {
            constValue = ((IceConstantData) constValue).castTo(constType);
        }
        constValue.setName(name);
        return (IceConstantData) constValue;
    }
}
