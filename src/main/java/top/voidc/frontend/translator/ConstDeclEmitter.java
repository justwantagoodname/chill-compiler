package top.voidc.frontend.translator;

import org.antlr.v4.runtime.tree.ParseTree;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
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

    protected IceValue visitArrayInitValExp(SysyParser.ExpContext ctx, IceType targetType) {
        // 内部是一个表达式，这个表达式应该是一个常量表达式
        var constValue = ctx.accept(new ConstExpEvaluator(context));
        Log.should(constValue instanceof IceConstantData, "Const value should be constant");
        if (!constValue.getType().equals(targetType)) {
            constValue = ((IceConstantData) constValue).castTo(targetType);
        }
        return constValue;
    }

    /**
     * 首先跑到最内侧的一维数组，判断遇到的是不是{}或者{0},如果是那就填完当前行跑路填下一行
     * 如果不是按顺序填充满当前行然后继续填充下一行
     * @param arrayDecl 当前的维度的数组
     * @param initVal 当前所在的语法节点
     * @param arraySize 声明的数组大小
     * @param depth 当前所在的数组的维度
     */
    protected void fillArray(IceConstantArray arrayDecl, SysyParser.InitValContext initVal, List<Integer> arraySize, int depth) {
        Log.should(depth < arraySize.size(), "Array size should be consistent with dimension");

        if (visited.contains(initVal)) return;
//        visited.add(initVal);
        if (initVal.initVal().isEmpty() && initVal.exp() == null) {
            // 是 {}，填 0 跑路
            arrayDecl.setZeroInit(true);
            visited.add(initVal);
            return;
        }

        final var isInLastDimension = depth == arraySize.size() - 1; // 当前维度是否是最后一维

        if (!isInLastDimension) {
            for (final var initValItem : initVal.initVal()) {
                if (visited.contains(initValItem)) continue; // 访问过了直接跳过

                if (arrayDecl.getType().getElementType().isArray()) {
                    // 内部还是数组
                    if (initValItem.initVal().isEmpty() && initValItem.exp() != null) {
                        // 内部是一个Exp
                        // 注意 initVal 这里是用于切换当前
                        final var filledSubArray = fillSubArray(arrayDecl, arraySize, depth, initVal); // 已经填充过的当前维度的一个子数组
                        arrayDecl.addElement(filledSubArray);
                    } else {
                        final var filledSubArray = fillSubArray(arrayDecl, arraySize, depth, initValItem); // 已经填充过的当前维度的一个子数组
                        arrayDecl.addElement(filledSubArray);
                    }
                } else {
                    throw new CompilationException("多余的{}", initVal, context);
                }
                visited.add(initValItem);
            }
            final var nestedArray =
                    new IceConstantArray((IceArrayType) arrayDecl.getType().getElementType(), List.of());
            nestedArray.setZeroInit(true);
            arrayDecl.fillLastWith(nestedArray);
        } else {
            for (final var initValItem : initVal.initVal()) { // 遍历当前初始化列表中的每一个元素
                if (visited.contains(initValItem)) continue; // 访问过了直接跳过

                if (arrayDecl.isFull()) {
                    // 填满一行了返回填下一行
                    return;
                }

                visited.add(initValItem);
                if (initValItem.initVal().isEmpty() && initValItem.exp() == null) {
                    // 是 {}，填 0 跑路
                    arrayDecl.setZeroInit(true);
                    return;
                } else if (initValItem.initVal().isEmpty() && initValItem.exp() != null) {
                    // 直接获取内部的表达式
                    var initValue = visitArrayInitValExp(initValItem.exp(), arrayDecl.getInsideType());
                    arrayDecl.addElement(initValue);
                } else {
                    throw new CompilationException("错误的初始化表达式", initVal, context);
                }
            }
            //  剩余的部分初始化为 0
            arrayDecl.fillLastWithZero();
        }
    }

    /**
     * 比如int a[3][3] => 给当前的arrayDecl填满一个int[3]
     * 填充子数组，保证填满
     * 要保证下面有维度才行不能是空的
     * @param arrayDecl 传入的*整体*，如int[3][3]
     * @param arraySize 所有总的大小
     * @param depth 当前的维度相对于arrayDecl的深度 比如 int[3] 在 int[3][3]
     * @param initValItem 要初始化维度的元素，例子中的int[3]
     */
    private IceConstantArray fillSubArray(IceConstantArray arrayDecl,
                                 List<Integer> arraySize,
                                 int depth,
                                 SysyParser.InitValContext initValItem) {
        final var subArrayType = arrayDecl.getType().getElementType();

        assert subArrayType.isArray(); // 保证是数组

        final var nestedArray = new IceConstantArray((IceArrayType) subArrayType, List.of());

        fillArray(nestedArray, initValItem, arraySize, depth + 1);

        return nestedArray;
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

        visited.clear();
        fillArray(arrayDecl, ctx.initVal(), arraySize, 0);
        visited.clear();

        arrayDecl.setName(null);

        final var constArrayDecl = new IceGlobalVariable(name, arrayDecl.getType(), arrayDecl);
        ((IcePtrType<?>) constArrayDecl.getType()).setConst(true);

        context.getSymbolTable().put(name, constArrayDecl);
        constants.add(constArrayDecl);
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
//        constants.add(constValue); // 不用加入全局常量中
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
            arraySize.add(((IceConstantInt) constValue).getValue());
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
                case F32 -> globalVarDecl.setInitializer(IceConstantData.create(0.0F));
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
