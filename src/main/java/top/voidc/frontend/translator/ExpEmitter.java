package top.voidc.frontend.translator;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.xpath.XPath;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;
import top.voidc.misc.StreamTools;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 用于翻译*单个*表达式为 IceIR，注意生成的表达式会被插入到当前的基本块的最后
 * 每个visit方法都返回一个 IceType 用于类型检查和插入类型转换
 */
public class ExpEmitter extends SysyBaseVisitor<IceValue> {
    protected final IceBlock block; // 当前基本块
    protected final IceContext context; // 上下文

    public ExpEmitter(IceContext context, IceBlock block) {
        this.block = block;
        this.context = context;
    }

    protected IceValue visitLogicalExp(SysyParser.ExpContext ctx) {
        throw new CompilationException("逻辑表达式不支持", ctx, context);
    }

    protected IceValue visitLogicNotExp(SysyParser.ExpContext ctx) {
        throw new CompilationException("逻辑非表达式不支持", ctx, context);
    }

    protected boolean isInCond(SysyParser.ExpContext ctx) {
        ParserRuleContext cur = ctx;
        while (cur.getParent() != null) {
            if (cur instanceof SysyParser.CondContext) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    private boolean shouldUseInvertLogical(SysyParser.ExpContext ctx) {
        if (!isInCond(ctx)) return false;
        ParserRuleContext cur = ctx;
        while (cur instanceof SysyParser.ExpContext expContext) {
            if (expContext.relOp != null || expContext.arithOp != null) return false;
            if (expContext.unaryOp != null
                    && !expContext.unaryOp.getText().equals("!")) return false;
            cur = cur.getParent();
        }
        return true;
    }

    private IceValue visitUnaryExp(SysyParser.ExpContext ctx) {
        // 一元运算符
        final var innerValue = visit(ctx.exp(0));
        switch (ctx.unaryOp.getText()) {
            case "+" -> {
                // 一元加法什么也不做
                return innerValue;
            }
            case "-" -> {
                // 取负
                switch (innerValue.getType().getTypeEnum()) {
                    case I32, F32 -> {
                        // 生成负数指令
                        final var instr = new IceNegInstruction(block, innerValue.getType(), innerValue);
                        block.addInstruction(instr);
                        return instr;
                    }
                    case I1 -> {
                        IceInstruction instr = new IceConvertInstruction(block, IceType.I32, innerValue);
                        block.addInstruction(instr);
                        instr = new IceNegInstruction(block, IceType.I32, instr);
                        block.addInstruction(instr);
                        return instr;
                    }
                    default -> throw new CompilationException("无法对 " + innerValue.getType() + " 取负", ctx,
                            context);
                }
            }
            case "!" -> {
                // 决定是值类型还是翻转控制流
                if (shouldUseInvertLogical(ctx)) {
                    return this.visitLogicNotExp(ctx);
                }
                switch (innerValue.getType().getTypeEnum()) {
                    case I32 -> {
                        // 生成非指令
                        final var instr = new IceIcmpInstruction(block, IceCmpInstruction.CmpType.EQ,
                                innerValue, IceConstantData.create(null, 0));
                        block.addInstruction(instr);
                        return instr;
                    }
                    case F32 -> {
                        // 生成非指令
                        final var instr = new IceFcmpInstruction(block, IceCmpInstruction.CmpType.EQ,
                                innerValue, IceConstantData.create(null, 0F));
                        block.addInstruction(instr);
                        return instr;
                    }
                    case I1 -> {
                        // 生成非指令
                        final var instr = new IceIcmpInstruction(block, IceCmpInstruction.CmpType.EQ,
                                innerValue, IceConstantData.create(null, false));
                        block.addInstruction(instr);
                        return instr;
                    }
                    default -> throw new CompilationException("无法对 " + innerValue.getType() + " 取非", ctx,
                            context);
                }
            }
            default -> throw new CompilationException("未知的运算符: " + ctx.unaryOp.getText(), ctx, context);
        }
    }

    private IceValue visitBinaryExp(SysyParser.ExpContext ctx) {
        // 二元运算符
        var lhsValue = visit(ctx.exp(0));
        var rhsValue = visit(ctx.exp(1));

        if (!lhsValue.getType().equals(rhsValue.getType())) {
            if (!lhsValue.getType().isConvertibleTo(rhsValue.getType())) {
                throw new CompilationException("无法对 " + lhsValue.getType() + " 和 " + rhsValue.getType() + " 进行运算", ctx,
                        context);
            }
            // 如果两侧类型不同，进行类型转换
            if (lhsValue.getType().compareTo(rhsValue.getType()) < 0) {
                // lhsValue 转换为 rhsValue
                final var instr = new IceConvertInstruction(block, rhsValue.getType(), lhsValue);
                block.addInstruction(instr);
                lhsValue = instr;
            } else {
                // rhsValue 转换为 lhsValue
                final var instr = new IceConvertInstruction(block, lhsValue.getType(), rhsValue);
                block.addInstruction(instr);
                rhsValue = instr;
            }
        }

        if (!lhsValue.getType().isNumeric() || !rhsValue.getType().isNumeric()) {
            // 如果不是数字类型，抛出异常
            throw new CompilationException("无法对 " + lhsValue.getType() + " 和 " + rhsValue.getType() + " 进行运算", ctx,
                    context);
        }

        // 生成指令
        IceInstruction instr;
        if (ctx.relOp != null) {
            // 关系运算符
            final var instrOp = IceCmpInstruction.CmpType.fromSysyLiteral(ctx.relOp.getText(),
                    lhsValue.getType().isFloat());
            if (lhsValue.getType().isInteger()) {
                instr = new IceIcmpInstruction(block, instrOp, lhsValue, rhsValue);
            } else {
                // 浮点数比较
                instr = new IceFcmpInstruction(block, instrOp, lhsValue, rhsValue);
            }
        } else {
            // 算术运算符
            final var instrOp = IceInstruction.InstructionType.fromSysyLiteral(ctx.arithOp.getText());
            instr = new IceBinaryInstruction(block, instrOp, lhsValue.getType(), lhsValue, rhsValue);
        }
        block.addInstruction(instr);
        return instr;
    }

    @Override
    public IceValue visitExp(SysyParser.ExpContext ctx) {
        // 判断表达式类型
        if (ctx.unaryOp != null) {
            // 一元运算符
            return visitUnaryExp(ctx);
        } else if (ctx.arithOp != null || ctx.relOp != null) {
            // 二元运算符
            return visitBinaryExp(ctx);
        } else if (ctx.logicOp != null) {
            // 逻辑运算符
            return visitLogicalExp(ctx);
        }

        // 其他情况向下遍历
        return super.visitExp(ctx);
    }

    @Override
    public IceConstantData visitNumber(SysyParser.NumberContext ctx) {
        final var literal = ctx.getText();

        if (ctx.IntConst() != null) {
            if (literal.startsWith("0x") || literal.startsWith("0X")) {
                return IceConstantData.create(null, Long.decode(literal));
            } else {
                return IceConstantData.create(null, Long.parseLong(literal));
            }
        } else if (ctx.FloatConst() != null) {
            return IceConstantData.create(null, Float.parseFloat(literal));
        }

        throw new CompilationException("未知的数字类型: " + literal, ctx, context);
    }

    @Override
    public IceConstantData visitString(SysyParser.StringContext ctx) {
        final var literal = ctx.getText();
        final var content = literal.substring(1, literal.length() - 1);
        return IceConstantData.create(content);
    }

    private static final Map<String, String> SPECIAL_FUNC = Map.of(
            "starttime", "_sysy_starttime",
            "stoptime", "_sysy_stoptime"
    );

    public String wrapFunctionName(String name) {
        return SPECIAL_FUNC.getOrDefault(name, name);
    }

    public boolean isSpecialFunction(String name) {
        return SPECIAL_FUNC.containsKey(name);
    }

    /**
     * 处理函数调用，顺带检查参数数量和类型，并作出相应的转换
     * @param ctx the parse tree
     */
    @Override
    public IceValue visitFuncCall(SysyParser.FuncCallContext ctx) {
//        Log.d("visitFuncCall " + ctx.getText());

        // 得到参数的值
        final List<IceValue> arguments = new java.util.ArrayList<>(ctx.funcRParams() == null ? List.of() :
                ctx.funcRParams().exp()
                        .stream()
                        .map(this::visit)
                        .toList());

        final var function = context.getSymbolTable().getFunction(wrapFunctionName(ctx.Ident().getText()))
                .orElseThrow(
                        () -> new CompilationException("找不到对函数 " + ctx.Ident().getText() + " 的定义", ctx, context));

        if (isSpecialFunction(ctx.Ident().getText())) {
            // 特殊函数 参数是行号
            arguments.add(IceConstantInt.create(null, ctx.getStart().getLine()));
        }

        // 检查参数数量
        if (function.getParameters().size() != arguments.size()) {
            throw new CompilationException("函数 " + ctx.Ident().getText() + " 参数数量不匹配", ctx, context);
        }

        // 检查参数类型
        StreamTools.zip(arguments.stream(), function.getParameters().stream(), (arg, param) -> {
            if (!arg.getType().equals(param.getType())) {

                if (!arg.getType().isConvertibleTo(param.getType())) {
                    throw new CompilationException(
                            "函数 " + ctx.Ident().getText() + " 参数类型不匹配", ctx, context);
                }

                final var instr = new IceConvertInstruction(block, param.getType(), arg);
                block.addInstruction(instr);
                return instr;
            } else {
                return arg;
            }
        });

        final var instr = new IceCallInstruction(block, function, arguments);
        block.addInstruction(instr);
        return instr;
    }

    /**
     * @return true 如果当前的 lVal 是函数调用的参数中的表达式
     */
    private boolean isInFuncCall(SysyParser.LValContext ctx) {
        ParserRuleContext cur = ctx;
        while (cur.getParent() != null) {
            if (cur instanceof SysyParser.FuncCallContext) {
                return true;
            }
            cur = cur.getParent();
        }
        return false;
    }

    private boolean isInAssignStmt(SysyParser.LValContext ctx) {
        return ctx.parent instanceof SysyParser.AssignStmtContext;
    }

    @Override
    protected IceValue aggregateResult(IceValue aggregate, IceValue nextResult) {
        if (aggregate == null) {
            return nextResult;
        } else {
            return aggregate;
        }
    }

    /**
     * 处理数组下标访问并生成GEP指令
     */
    private IceGEPInstruction handleArrayAccess(String name, IceValue targetVariable, SysyParser.LValContext ctx, boolean needFirstZero) {
        // 处理数组下标
        final var indices = new ArrayList<>(ctx.exp().stream()
                .map(this::visit)
                .map(indexVal -> {
                    if (indexVal.getType().isInteger()) {
                        return indexVal;
                    }
                    if (!indexVal.getType().isConvertibleTo(IceType.I32)) {
                        // 下标不是整型
                        throw new CompilationException("数组 " + name + " 的下标访问无法转换到 int 类型", ctx, context);
                    } else {
                        // 下标转换为整型
                        final var convertInstr = new IceConvertInstruction(block, IceType.I32, indexVal);
                        block.addInstruction(convertInstr);
                        return convertInstr;
                    }
                }).toList());

        // 对于[i32 x N]*类型的数组(全局数组和栈上数组)需要第一个0偏移
        if (needFirstZero) {
            indices.add(0, IceConstantInt.create(null, 0));
        }

        final var gepInstr = new IceGEPInstruction(block, targetVariable, indices);
        block.addInstruction(gepInstr);
        return gepInstr;
    }

    /**
     * 解析对于变量的访问
     * 需要决定是值还是指针
     * @param ctx the parse tree
     */
    @Override
    public IceValue visitLVal(SysyParser.LValContext ctx) {
        // 变量名
        final var name = ctx.Ident().getText();
        // 变量类型
        var targetVariable = context.getSymbolTable().get(name)
                .orElseThrow(() -> new CompilationException("找不到变量 " + name, ctx, context));

        // 处理常量的情况
        if (targetVariable instanceof IceConstantData) {
            return ((IceConstantData) targetVariable).clone();
        }

        // 确保目标变量是指针类型
        Log.should(targetVariable.getType().isPointer(), "目标变量 " + targetVariable.getName() + " 不是指针类型");

        // 处理数组访问
        if (ctx.exp().isEmpty()) {
            // 没有数组下标访问
            if (isInAssignStmt(ctx)) {
                // 在赋值的左边，直接返回指针
                return targetVariable;
            } else {
                // 其他情况，加载ptr指向的变量
                final var loadInstr = new IceLoadInstruction(block, targetVariable);
                block.addInstruction(loadInstr);
                return loadInstr;
            }
        }

        // 有数组下标访问，进行类型检查
        if (!targetVariable.getType().isPointer()) {
            throw new CompilationException("无法对 " + name + " 进行下标访问", ctx, context);
        }

        IceValue arrayPtr = targetVariable;
        if (targetVariable instanceof IceGlobalVariable) {
            // 全局数组：[i32 x N]*类型，直接用GEP，需要第一个0偏移
            final var gepInstr = handleArrayAccess(name, arrayPtr, ctx, true);
            if (isInAssignStmt(ctx)) {
                return gepInstr;
            } else {
                final var loadInstr = new IceLoadInstruction(block, gepInstr);
                block.addInstruction(loadInstr);
                return loadInstr;
            }
        } else if (targetVariable.getType() instanceof IcePtrType<?> arrayPtrType) {
            if (arrayPtrType.getPointTo().isPointer()) {
                // 参数数组：i32*类型，先load获取i32*，然后直接GEP计算偏移
                final var loadInstr = new IceLoadInstruction(block, arrayPtr);
                block.addInstruction(loadInstr);
                arrayPtr = loadInstr;
                
                final var gepInstr = handleArrayAccess(name, arrayPtr, ctx, false);
                if (isInAssignStmt(ctx)) {
                    return gepInstr;
                } else {
                    final var finalLoadInstr = new IceLoadInstruction(block, gepInstr);
                    block.addInstruction(finalLoadInstr);
                    return finalLoadInstr;
                }
            } else if (arrayPtrType.getPointTo().isArray()) {
                // 栈上数组：[i32 x N]*类型，直接用GEP，需要第一个0偏移
                final var gepInstr = handleArrayAccess(name, arrayPtr, ctx, true);
                if (isInAssignStmt(ctx)) {
                    return gepInstr;
                } else {
                    final var loadInstr = new IceLoadInstruction(block, gepInstr);
                    block.addInstruction(loadInstr);
                    return loadInstr;
                }
            }
        }

        throw new CompilationException("无法识别的数组类型: " + targetVariable.getType(), ctx, context);
    }
}
