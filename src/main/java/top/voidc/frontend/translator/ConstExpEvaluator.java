package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.frontend.translator.exception.EvaluationException;
import top.voidc.ir.*;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
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

    public IceConstantData evaluate(String op, IceConstantData exp) {
        return switch (exp.getType().getTypeEnum()) {
            case I32 -> {
                final var value = ((IceConstantInt) exp).getValue();
                yield switch (op) {
                    case "-" -> IceConstantData.create(-value);
                    case "+" -> exp;
                    case "!" -> IceConstantData.create(~value);
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
            }
            case F32 -> {
                final var value = ((IceConstantFloat) exp).getValue();
                yield switch (op) {
                    case "-" -> IceConstantData.create(-value);
                    case "+" -> exp;
                    case "!" -> IceConstantData.create(value == 0 ? 1 : 0);
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
            }
            default -> throw new IllegalStateException("Unexpected value: " + exp.getType());
        };
    }

    public IceConstantData evaluate(String op, IceConstantData lhs, IceConstantData rhs) {
        final var lhsType = lhs.getType();
        final var rhsType = rhs.getType();

        if (lhsType != rhsType) {
            // cast
            if (lhsType == IceType.I32()) {
                lhs = lhs.castTo(IceType.F32());
            } else {
                rhs = rhs.castTo(IceType.F32());
            }
        }

        return switch (lhs.getType().getTypeEnum()) {
            case I32 -> {
                long lhsValue = ((IceConstantInt) lhs).getValue();
                long rhsValue = ((IceConstantInt) rhs).getValue();

                yield switch (op) {
                    case "+" -> IceConstantData.create(lhsValue + rhsValue);
                    case "-" -> IceConstantData.create(lhsValue - rhsValue);
                    case "*" -> IceConstantData.create(lhsValue * rhsValue);
                    case "/" -> IceConstantData.create(lhsValue / rhsValue);
                    case "%" -> IceConstantData.create(lhsValue % rhsValue);
                    case "&&" -> IceConstantData.create(lhsValue != 0 && rhsValue != 0);
                    case "||" -> IceConstantData.create(lhsValue != 0 || rhsValue != 0);
                    case "<" -> IceConstantData.create(lhsValue < rhsValue);
                    case ">" -> IceConstantData.create(lhsValue > rhsValue);
                    case "<=" -> IceConstantData.create(lhsValue <= rhsValue);
                    case ">=" -> IceConstantData.create(lhsValue >= rhsValue);
                    case "==" -> IceConstantData.create(lhsValue == rhsValue);
                    case "!=" -> IceConstantData.create(lhsValue != rhsValue);
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
            }
            case F32 -> {
                float lhsValue = ((IceConstantFloat) lhs).getValue();
                float rhsValue = ((IceConstantFloat) rhs).getValue();

                yield switch (op) {
                    case "+" -> IceConstantData.create(lhsValue + rhsValue);
                    case "-" -> IceConstantData.create(lhsValue - rhsValue);
                    case "*" -> IceConstantData.create(lhsValue * rhsValue);
                    case "/" -> IceConstantData.create(lhsValue / rhsValue);
                    case "&&" -> IceConstantData.create(lhsValue != 0 && rhsValue != 0);
                    case "||" -> IceConstantData.create(lhsValue != 0 || rhsValue != 0);
                    case "<" -> IceConstantData.create(lhsValue < rhsValue);
                    case ">" -> IceConstantData.create(lhsValue > rhsValue);
                    case "<=" -> IceConstantData.create(lhsValue <= rhsValue);
                    case ">=" -> IceConstantData.create(lhsValue >= rhsValue);
                    case "==" -> IceConstantData.create(lhsValue == rhsValue);
                    case "!=" -> IceConstantData.create(lhsValue != rhsValue);
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
            }
            default -> throw new IllegalStateException("Unexpected value: " + lhsType);
        };
    }

    @Override
    public IceConstant visitExp(SysyParser.ExpContext ctx) {
        // 判断表达式类型
        if (ctx.unaryOp != null) {
            // one operands
            final var exp = visit(ctx.exp(0));
            Log.should(isConst(exp), "exp must be const here.");
            return evaluate(ctx.unaryOp.getText(), (IceConstantData) exp);
        }

        if (ctx.arithOp != null || ctx.logicOp != null || ctx.relOp != null) {
            // two operands
            final var lhs = visit(ctx.exp(0));
            final var rhs = visit(ctx.exp(1));

            if (ctx.arithOp != null) {
                if (isConst(lhs) && isConst(rhs)) {
                    return evaluate(ctx.arithOp.getText(), (IceConstantData) lhs, (IceConstantData) rhs);
                }
            }

            if (ctx.logicOp != null) {
                if (isConst(lhs) && isConst(rhs)) {
                    return evaluate(ctx.logicOp.getText(), (IceConstantData) lhs, (IceConstantData) rhs);
                }
            }

            if (ctx.relOp != null) {
                if (isConst(lhs) && isConst(rhs)) {
                    return evaluate(ctx.relOp.getText(), (IceConstantData) lhs, (IceConstantData) rhs);
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
