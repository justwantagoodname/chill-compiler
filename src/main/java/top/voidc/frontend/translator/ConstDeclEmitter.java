package top.voidc.frontend.translator;

import org.antlr.v4.runtime.tree.ParseTree;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.*;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 遍历全局和局部范围内的中的常量和全局变量，要求区别是要求所有的初值必须是常量或者编译期可计算的表达式
 */

/**
 * TODO: 对于局部常量和变量还得特殊处理下
 */

public class ConstDeclEmitter extends SysyBaseVisitor<Void> {
    private final Set<SysyParser.InitValContext> visited = new HashSet<>();
    private final IceContext context;
    private final List<IceConstant> constants = new ArrayList<>();

    public ConstDeclEmitter(IceContext context) {
        this.context = context;
    }

    public List<IceConstant> emitConstDecl(ParseTree root) {
        this.visit(root);
        return constants;
    }

    @Override
    public Void visitDecl(SysyParser.DeclContext ctx) {
        Log.should(ctx.parent instanceof SysyParser.CompUnitContext, "This can only handle global variable");
        return super.visitDecl(ctx);
    }

    private void fillArray(IceConstantArray arrayDecl, SysyParser.InitValContext initVal, ArrayList<Integer> arraySize, int depth) {
        Log.should(depth < arraySize.size(), "Array size should be consistent with dimension");

        var currentArraySize = arraySize.get(depth); // 当前类型(维度)下剩余填充需要的元素数量

        for (final var initValItem : initVal.initVal()) {
            if (visited.contains(initValItem)) continue;
            if (currentArraySize == 0) return; // 当前维度已经填充完毕，直接返回填充下一个维度

            if (initValItem.initVal().isEmpty() && initValItem.exp() != null) {
                if (arraySize.get(depth + 1) == 1) {
                    // 内部是一个表达式，这个表达式应该是一个常量表达式
                    var constValue = initValItem.exp().accept(new ConstExpEvaluator(context));
                    Log.should(constValue instanceof IceConstantData, "Const value should be constant");
                    // 最后一维度，直接添加到数组中
                    if (!constValue.getType().equals(arrayDecl.getInsideType())) {
                        constValue = ((IceConstantData) constValue).castTo(arrayDecl.getInsideType());
                    }
                    arrayDecl.addElement((IceConstantData) constValue);
                    currentArraySize--;
                } else {
                    // 还有下一维度，递归填充
                    currentArraySize = fillSubArray(arrayDecl, arraySize, depth, currentArraySize, initVal);
                }
            } else {
                // 内部是一个数组，递归填充
                currentArraySize = fillSubArray(arrayDecl, arraySize, depth, currentArraySize, initValItem);
            }
            visited.add(initValItem);
        }
        if (currentArraySize > 0) {
            // 内部是一个数组，递归填充
            final var subArrayType = ((IceArrayType) arrayDecl.getType()).getElementType();
            if (subArrayType instanceof IceArrayType) {
                final var nestedArray = new IceConstantArray(null, (IceArrayType) subArrayType);
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

    private Integer fillSubArray(IceConstantArray arrayDecl, ArrayList<Integer> arraySize, int depth, Integer currentArraySize, SysyParser.InitValContext initValItem) {
        final var subArrayType = ((IceArrayType) arrayDecl.getType()).getElementType();
        final var nestedArray = new IceConstantArray(null, (IceArrayType) subArrayType, new ArrayList<>());
        fillArray(nestedArray, initValItem, arraySize, depth + 1);
        arrayDecl.addElement(nestedArray);
        currentArraySize--;
        return currentArraySize;
    }

    public IceConstantArray visitConstArrayDef(SysyParser.ConstDefContext ctx) {
        final var typeLiteral = ((SysyParser.ConstDeclContext) ctx.parent).primitiveType().getText();
        final var elementType = IceType.fromSysyLiteral(typeLiteral);
        final var name = ctx.Ident().getText();
        final var arraySize = new ArrayList<Integer>();

        for (var exp : ctx.constExp()) {
            var constValue = exp.accept(new ConstExpEvaluator(context));
            Log.should(constValue instanceof IceConstantInt, "Const value should be constant");
            arraySize.add((int) ((IceConstantInt) constValue).getValue());
        }
        final var arrayType = IceArrayType.buildNestedArrayType(arraySize, elementType);

        final var arrayDecl = new IceConstantArray(name, arrayType, new ArrayList<>());

        // compute dimension size
        arraySize.add(1);

        visited.clear();
        fillArray(arrayDecl, ctx.initVal(), arraySize, 0);
        visited.clear();

        arrayDecl.setName(name);

        return arrayDecl;
    }

    @Override
    public Void visitConstDef(SysyParser.ConstDefContext ctx) {
        final boolean isArray = !ctx.constExp().isEmpty();

        if (isArray) {
            final var arrDef = visitConstArrayDef(ctx);
            context.getSymbolTable().put(arrDef.getName(), arrDef);
            constants.add(arrDef);
            return null;
        }

        final var typeLiteral = ((SysyParser.ConstDeclContext) ctx.parent).primitiveType().getText();
        var constType = IceType.fromSysyLiteral(typeLiteral);

        final var name = ctx.Ident().getText();

        final var constExp = ctx.initVal().exp();

        var constValue = constExp.accept(new ConstExpEvaluator(context));

        Log.should(constValue instanceof IceConstantData, "Const value should be constant");
        if (!constType.equals(constValue.getType())) {
            constValue = ((IceConstantData) constValue).castTo(constType);
        }
        constValue.setName(name);
        context.getSymbolTable().put(constValue.getName(), constValue);
        constants.add(constValue);
        return null;
    }

    private IceConstant visitVarArrayDecl(SysyParser.VarDefContext ctx) {
        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
        final var elementType = IceType.fromSysyLiteral(typeLiteral);
        final var name = ctx.Ident().getText();
        final var arraySize = new ArrayList<Integer>();

        for (var exp : ctx.constExp()) {
            var constValue = exp.accept(new ConstExpEvaluator(context));
            Log.should(constValue instanceof IceConstantInt, "index value should be constant");
            arraySize.add((int) ((IceConstantInt) constValue).getValue());
        }
        final var arrayType = IceArrayType.buildNestedArrayType(arraySize, elementType);
        final var globalVarArray = new IceGlobalVariable(name, arrayType, null);

        if (ctx.initVal() != null) {
            final var initVal = ctx.initVal();
            final var initArray = new IceConstantArray(name, arrayType, new ArrayList<>());
            // compute dimension size
            arraySize.add(1);

            visited.clear();
            fillArray(initArray, initVal, arraySize, 0);
            visited.clear();

            globalVarArray.setInitializer(initArray);
        }
        return globalVarArray;
    }

    @Override
    public Void visitVarDef(SysyParser.VarDefContext ctx) {
        final boolean isArray = !ctx.constExp().isEmpty();

        if (isArray) {
            final var arrDef = visitVarArrayDecl(ctx);
            context.getSymbolTable().put(arrDef.getName(), arrDef);
            constants.add(arrDef);
            return null;
        }

        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
        final var varType = IceType.fromSysyLiteral(typeLiteral);
        final var name = ctx.Ident().getText();
        final var globalVarDecl = new IceGlobalVariable(name, varType, null);

        if (ctx.initVal() != null) {
            final var initExp = ctx.initVal().exp(); // initExp must be a constExp and is optional
            IceConstantData initValue;

            if (initExp != null) {
                initValue = (IceConstantData) initExp.accept(new ConstExpEvaluator(context));
                Log.should(initValue != null, "Const value should be constant");
                if (!varType.equals(initValue.getType())) {
                    initValue = initValue.castTo(varType);
                }
                globalVarDecl.setInitializer(initValue);
            }
        }

        context.getSymbolTable().put(globalVarDecl.getName(), globalVarDecl);
        constants.add(globalVarDecl);
        return null;
    }
}
