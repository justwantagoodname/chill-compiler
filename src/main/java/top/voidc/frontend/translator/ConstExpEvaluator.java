package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.frontend.translator.exception.EvaluationException;
import top.voidc.ir.*;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.NotNull;

/**
 * 常量表达式求值，若输出是非常量表达式，抛出异常
 */
public class ConstExpEvaluator extends SysyBaseVisitor<IceConstant> {

    private final IceContext context;

    public ConstExpEvaluator(IceContext context) {
        this.context = context;
    }

    /**
     * 判断是否是常量表达式, 用于常量折叠
     *
     * @param exp 表达式
     * @return 是否是常量表达式
     */
    public boolean isConst(IceValue exp) {
        return exp instanceof IceConstantData;
    }

    @Override
    public IceConstant visitExp(SysyParser.ExpContext ctx) {
        // 判断表达式类型
        if (ctx.unaryOp != null) {
            // one operand
            final var exp = visit(ctx.exp(0));
            Log.should(isConst(exp), "exp must be const here.");
            final var constExp = (IceConstantData) exp;
            
            return switch (ctx.unaryOp.getText()) {
                case "-" -> IceConstantData.create(0).minus(constExp);
                case "+" -> constExp;
                case "!" -> constExp.not(constExp);
                default -> throw new IllegalStateException("Unexpected value: " + ctx.unaryOp.getText());
            };
        }

        if (ctx.arithOp != null || ctx.logicOp != null || ctx.relOp != null) {
            // two operands
            final var lhs = visit(ctx.exp(0));
            final var rhs = visit(ctx.exp(1));

            if (isConst(lhs) && isConst(rhs)) {
                final var constLhs = (IceConstantData) lhs;
                final var constRhs = (IceConstantData) rhs;

                if (ctx.arithOp != null) {
                    return switch (ctx.arithOp.getText()) {
                        case "+" -> constLhs.plus(constRhs);
                        case "-" -> constLhs.minus(constRhs);
                        case "*" -> constLhs.multiply(constRhs);
                        case "/" -> constLhs.divide(constRhs);
                        case "%" -> constLhs.mod(constRhs);
                        default -> throw new IllegalStateException("Unexpected value: " + ctx.arithOp.getText());
                    };
                }

                if (ctx.logicOp != null) {
                    return switch (ctx.logicOp.getText()) {
                        case "&&" -> constLhs.and(constRhs);
                        case "||" -> constLhs.or(constRhs);
                        default -> throw new IllegalStateException("Unexpected value: " + ctx.logicOp.getText());
                    };
                }

                if (ctx.relOp != null) {
                    return switch (ctx.relOp.getText()) {
                        case "<" -> constLhs.lt(constRhs);
                        case ">" -> constLhs.gt(constRhs);
                        case "<=" -> constLhs.le(constRhs);
                        case ">=" -> constLhs.ge(constRhs);
                        case "==" -> constLhs.eq(constRhs);
                        case "!=" -> constLhs.ne(constRhs);
                        default -> throw new IllegalStateException("Unexpected value: " + ctx.relOp.getText());
                    };
                }
            }
        }

        // 其他情况向下遍历
        return super.visitExp(ctx);
    }

    @Override
    public IceConstantData visitNumber(SysyParser.NumberContext ctx) {
        final var literal = ctx.getText();
        if (ctx.IntConst() != null) {
            return IceConstantData.create(Long.decode(literal));
        } else if (ctx.FloatConst() != null) {
            return IceConstantData.create(Float.parseFloat(literal));
        }
        return null;
    }

    @Override
    public IceConstantData visitString(SysyParser.StringContext ctx) {
        final var literal = ctx.getText();
        final var content = literal.substring(1, literal.length() - 1);
        return IceConstantData.create(content);
    }

    @Override
    public IceConstant visitFuncCall(SysyParser.FuncCallContext ctx) {
        throw new EvaluationException(ctx, context);
    }

    /**
     * 访问常量数组，获得常量
     *
     */
    public IceConstant fetchConstValue(@NotNull IceConstantArray target, SysyParser.LValContext ctx) {
        Log.should(!ctx.exp().isEmpty(), "Array access should have one index");
        final var arrayRefValues = ctx.exp().stream().map(this::visit).toList();
        if (arrayRefValues.stream().anyMatch(exp -> !isConst(exp))) {
            throw new EvaluationException(ctx, context);
        }
        final var constArrayRef = arrayRefValues.stream()
                .map(exp -> (int) ((IceConstantInt) exp).getValue()).toList();
        final var constValue = target.get(constArrayRef);
        if (!(constValue instanceof IceConstantData)) {
            throw new EvaluationException(ctx, context);
        }
        return ((IceConstantData) constValue).clone();
    }

    /**
     * 访问常量若访问变量异常将被抛出
     *
     * @param ctx the parse tree
     * @return LVal引用的常量
     */
    @Override
    public IceConstant visitLVal(SysyParser.LValContext ctx) {
        final var target = ctx.Ident().getText();
        final var symbol = context.getSymbolTable().get(target).orElseThrow(
                () -> new CompilationException("找不到 " + target + " 的定义", ctx, context)
        );

        if (symbol instanceof IceGlobalVariable globalVariable
            && globalVariable.getType() instanceof IcePtrType<?> variablePtrType
            && variablePtrType.isConst()
            && globalVariable.getInitializer() instanceof IceConstantArray globalVariableArray) {
            return fetchConstValue(globalVariableArray, ctx);
        } else if (symbol instanceof IceConstantData) {
            return ((IceConstantData) symbol).clone();
        } else {
            throw new EvaluationException(ctx, context);
        }
    }
}
