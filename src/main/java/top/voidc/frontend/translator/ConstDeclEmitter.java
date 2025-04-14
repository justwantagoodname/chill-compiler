package top.voidc.frontend.translator;

import org.antlr.v4.runtime.tree.ParseTree;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.*;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 遍历全局中的常量和全局变量，要求区别是要求所有的初值必须是常量或者编译期可计算的表达式
 * 得到的结果是一个常量的列表，其中的常量会自动加入到当前的符号表中
 * 对于局部常量和变量使用
 * 全局变量和常量数组 -> IceGlobalVariable
 * 常量 -> IceConstantData 立即数
 * */
public class ConstDeclEmitter extends SysyBaseVisitor<Void> {
    protected final Set<SysyParser.InitValContext> visited = new HashSet<>();
    protected final IceContext context;
    private final List<IceConstant> constants = new ArrayList<>();

    public ConstDeclEmitter(IceContext context) {
        this.context = context;
    }

    final public List<IceConstant> emitConstDecl(ParseTree root) {
        this.visit(root);
        return constants;
    }

    protected boolean isGlobalVarDecl(ParseTree ctx) {
        return ctx instanceof SysyParser.DeclContext declCtx && declCtx.parent instanceof SysyParser.CompUnitContext;
    }

    protected IceValue visitArrayInitValExp(SysyParser.ExpContext ctx, IceType targetType) {
        // 内部是一个表达式，这个表达式应该是一个常量表达式
        var constValue = ctx.accept(new ConstExpEvaluator(context));
        Log.should(constValue instanceof IceConstantData, "Const value should be constant");
        if (!constValue.getType().equals(targetType)) {
            constValue = ((IceConstantData) constValue).castTo(targetType);
        }
        return constValue;
    }

    protected void fillArray(IceConstantArray arrayDecl, SysyParser.InitValContext initVal, List<Integer> arraySize, int depth) {
        Log.should(depth < arraySize.size(), "Array size should be consistent with dimension");

        var currentArraySize = arraySize.get(depth); // 当前类型(维度)下剩余填充需要的元素数量

        for (final var initValItem : initVal.initVal()) {
            if (visited.contains(initValItem)) continue;
            if (currentArraySize == 0) return; // 当前维度已经填充完毕，直接返回填充下一个维度

            if (initValItem.initVal().isEmpty() && initValItem.exp() != null) {
                if (arraySize.get(depth + 1) == 1) {
                    // 内部是一个表达式，这个表达式应该是一个常量表达式
                    var initValue = visitArrayInitValExp(initValItem.exp(), arrayDecl.getInsideType());

                    // 最后一维度，直接添加到数组中
                    arrayDecl.addElement(initValue);
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
                final var nestedArray = new IceConstantArray((IceArrayType) subArrayType);
                arrayDecl.addElement(nestedArray);
            } else {
                // 以0填充
                switch (arrayDecl.getInsideType().getTypeEnum()) {
                    case I32 -> arrayDecl.addElement(IceConstantData.create(0), currentArraySize);
                    case F32 -> arrayDecl.addElement(IceConstantData.create(0F), currentArraySize);
                    default -> throw new IllegalStateException("Unexpected value: " + arrayDecl.getType());
                }
            }
        }
    }

    private Integer fillSubArray(IceConstantArray arrayDecl,
                                 List<Integer> arraySize,
                                 int depth,
                                 Integer currentArraySize,
                                 SysyParser.InitValContext initValItem) {
        final var subArrayType = ((IceArrayType) arrayDecl.getType()).getElementType();
        final var nestedArray = new IceConstantArray((IceArrayType) subArrayType, new ArrayList<>());
        fillArray(nestedArray, initValItem, arraySize, depth + 1);
        arrayDecl.addElement(nestedArray);
        currentArraySize--;
        return currentArraySize;
    }

    protected IceType getConstType(SysyParser.ConstDefContext ctx) {
        final var typeLiteral = ((SysyParser.ConstDeclContext) ctx.parent).primitiveType().getText();
        return IceType.fromSysyLiteral(typeLiteral);
    }

    protected List<Integer> getConstDeclSize(SysyParser.ConstDefContext ctx) {
        final var arraySize = new ArrayList<Integer>();
        for (var exp : ctx.constExp()) {
            var constValue = exp.accept(new ConstExpEvaluator(context));
            Log.should(constValue instanceof IceConstantInt, "index value should be constant");
            arraySize.add((int) ((IceConstantInt) constValue).getValue());
        }
        return arraySize;
    }

    protected void visitConstArrayDef(SysyParser.ConstDefContext ctx) {
        final var elementType = getConstType(ctx);
        final var name = ctx.Ident().getText();
        final var arraySize = getConstDeclSize(ctx);
        final var arrayType = IceArrayType.buildNestedArrayType(arraySize, elementType);

        final var arrayDecl = new IceConstantArray(arrayType, new ArrayList<>());

        // compute dimension size
        arraySize.add(1);

        visited.clear();
        fillArray(arrayDecl, ctx.initVal(), arraySize, 0);
        visited.clear();

        arrayDecl.setName(null);

        final var constArrayDecl = new IceGlobalVariable(name, arrayDecl.getType(), arrayDecl);
        ((IcePtrType<?>) constArrayDecl.getType()).setConst(true);

        context.getSymbolTable().put(name, constArrayDecl);
        constants.add(arrayDecl);
    }

    /**
     * 常量直接变字面量
     */
    protected void visitConstSingleDef(SysyParser.ConstDefContext ctx) {
        final var constType = getConstType(ctx);
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
    }

    @Override
    public Void visitConstDef(SysyParser.ConstDefContext ctx) {
        final boolean isArray = !ctx.constExp().isEmpty();
        if (isArray) {
            visitConstArrayDef(ctx);
        } else {
            visitConstSingleDef(ctx);
        }
        return null;
    }

    protected List<Integer> getArrayDeclSize(SysyParser.VarDefContext ctx) {
        final var arraySize = new ArrayList<Integer>();
        for (var exp : ctx.constExp()) {
            var constValue = exp.accept(new ConstExpEvaluator(context));
            Log.should(constValue instanceof IceConstantInt, "index value should be constant");
            arraySize.add((int) ((IceConstantInt) constValue).getValue());
        }
        return arraySize;
    }

    /**
     * 访问全局变量数组定义
     * @param ctx the parse tree
     */
    protected void visitVarArrayDecl(SysyParser.VarDefContext ctx) {
        final var elementType = getVarType(ctx);
        final var name = ctx.Ident().getText();
        final var arraySize = getArrayDeclSize(ctx);

        final var arrayType = IceArrayType.buildNestedArrayType(arraySize, elementType);
        final var globalVarArray = new IceGlobalVariable(name, arrayType, null);

        if (ctx.initVal() != null) {
            final var initVal = ctx.initVal();
            final var initArray = new IceConstantArray(arrayType, new ArrayList<>());
            // compute dimension size
            arraySize.add(1);

            visited.clear();
            fillArray(initArray, initVal, arraySize, 0);
            visited.clear();

            globalVarArray.setInitializer(initArray);
        }
        context.getSymbolTable().put(globalVarArray.getName(), globalVarArray);
        constants.add(globalVarArray);
    }

    protected void visitSingleVarDecl(SysyParser.VarDefContext ctx) {
        final var varType = getVarType(ctx);
        final var name = ctx.Ident().getText();
        final var globalVarDecl = new IceGlobalVariable(name, varType, null);

        if (ctx.initVal() != null) {
            handleVarInitVal(ctx.initVal(), globalVarDecl);
        } else {
            switch (varType.getTypeEnum()) {
                case I32 -> globalVarDecl.setInitializer(IceConstantData.create(0));
                case F32 -> globalVarDecl.setInitializer(IceConstantData.create(0.0));
                default -> throw new IllegalStateException("Unexpected value: " + varType);
            }
        }

        context.getSymbolTable().put(globalVarDecl.getName(), globalVarDecl);
        constants.add(globalVarDecl);
    }

    private void handleVarInitVal(SysyParser.InitValContext ctx, IceGlobalVariable globalVarDecl) {
        final var initExp = ctx.exp(); // initExp must be a constExp and is optional
        final var varType = ((IcePtrType<?>)globalVarDecl.getType()).getPointTo();

        if (initExp != null) {
            var initValue = (IceConstantData) initExp.accept(new ConstExpEvaluator(context));
            Log.should(initValue != null, "Const value should be constant");
            if (!varType.equals(initValue.getType())) {
                initValue = initValue.castTo(varType);
            }
            globalVarDecl.setInitializer(initValue);
        }
    }

    protected IceType getVarType(SysyParser.VarDefContext ctx) {
        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
        return IceType.fromSysyLiteral(typeLiteral);
    }



    /**
     * 访问全局变量定义
     * @param ctx the parse tree
     */
    @Override
    public Void visitVarDef(SysyParser.VarDefContext ctx) {
        final var isArray = !ctx.constExp().isEmpty();
        if (isArray) {
            visitVarArrayDecl(ctx);
        } else {
            visitSingleVarDecl(ctx);
        }
        return null;
    }
}
