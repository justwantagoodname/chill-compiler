package top.voidc.frontend.translator;

import top.voidc.frontend.helper.SymbolTable;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.ir.*;
import top.voidc.ir.type.IceType;
import top.voidc.misc.Log;
import top.voidc.misc.Tool;

import java.util.ArrayList;

/**
 * 表达式翻译器，会尝试做初步的变量折叠，如果是纯常量表达式，那么保证化简至常数（不包括函数调用）
 * 对于包含常量引用的一并化简
 * 若包含变量引用那么会返回一个基本块
 */
public class ExpEvaluator extends SysyBaseVisitor<IceValue> {

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
                    case "-" -> IceConstantData.create(exp.getName(), -value);
                    case "+" -> exp;
                    case "!" -> IceConstantData.create(exp.getName(), ~value);
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
            }
            case F32 -> {
                final var value = ((IceConstantFloat) exp).getValue();
                yield switch (op) {
                    case "-" -> IceConstantData.create(null, -value);
                    case "+" -> exp;
                    case "!" -> IceConstantData.create(null, value == 0 ? 1 : 0);
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
                lhs = ((IceConstantInt) lhs).castTo(IceType.F32());
            } else {
                rhs = ((IceConstantInt) rhs).castTo(IceType.F32());
            }
        }

        return switch (lhsType.getTypeEnum()) {
            case I32 -> {
                long lhsValue = ((IceConstantInt) lhs).getValue();
                long rhsValue = ((IceConstantInt) rhs).getValue();

                yield switch (op) {
                    case "+" -> IceConstantData.create(null, lhsValue + rhsValue);
                    case "-" -> IceConstantData.create(null, lhsValue - rhsValue);
                    case "*" -> IceConstantData.create(null, lhsValue * rhsValue);
                    case "/" -> IceConstantData.create(null, lhsValue / rhsValue);
                    case "%" -> IceConstantData.create(null, lhsValue % rhsValue);
                    case "&&" -> IceConstantData.create(null, lhsValue & rhsValue);
                    case "||" -> IceConstantData.create(null, lhsValue | rhsValue);
                    case "<" -> IceConstantData.create(null, lhsValue < rhsValue ? 1 : 0);
                    case ">" -> IceConstantData.create(null, lhsValue > rhsValue ? 1 : 0);
                    case "<=" -> IceConstantData.create(null, lhsValue <= rhsValue ? 1 : 0);
                    case ">=" -> IceConstantData.create(null, lhsValue >= rhsValue ? 1 : 0);
                    case "==" -> IceConstantData.create(null, lhsValue == rhsValue ? 1 : 0);
                    case "!=" -> IceConstantData.create(null, lhsValue != rhsValue ? 1 : 0);
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
            }
            case F32 -> {
                double lhsValue = ((IceConstantFloat) lhs).getValue();
                double rhsValue = ((IceConstantFloat) rhs).getValue();

                yield switch (op) {
                    case "+" -> IceConstantData.create(null, lhsValue + rhsValue);
                    case "-" -> IceConstantData.create(null, lhsValue - rhsValue);
                    case "*" -> IceConstantData.create(null, lhsValue * rhsValue);
                    case "/" -> IceConstantData.create(null, lhsValue / rhsValue);
                    case "<" -> IceConstantData.create(null, lhsValue < rhsValue ? 1 : 0);
                    case ">" -> IceConstantData.create(null, lhsValue > rhsValue ? 1 : 0);
                    case "<=" -> IceConstantData.create(null, lhsValue <= rhsValue ? 1 : 0);
                    case ">=" -> IceConstantData.create(null, lhsValue >= rhsValue ? 1 : 0);
                    case "==" -> IceConstantData.create(null, lhsValue == rhsValue ? 1 : 0);
                    case "!=" -> IceConstantData.create(null, lhsValue != rhsValue ? 1 : 0);
                    default -> throw new IllegalStateException("Unexpected value: " + op);
                };
            }
            default -> throw new IllegalStateException("Unexpected value: " + lhsType);
        };
    }

    @Override
    public IceConstantData visitConstExp(SysyParser.ConstExpContext ctx) {
        final var evaluatedExp = visit(ctx.exp());
        Log.should(isConst(evaluatedExp), "ConstExp should be constant");
        return (IceConstantData) evaluatedExp;
    }

    @Override
    public IceValue visitExp(SysyParser.ExpContext ctx) {
        if (ctx.unaryOp != null) {
            // one operands
            final var exp = visit(ctx.exp(0));
            if (isConst(exp)) {
                return evaluate(ctx.unaryOp.getText(), (IceConstantData) exp);
            } else {
                // generate instruction
                Tool.TODO();
            }
        }

        if (ctx.arithOp != null || ctx.logicOp != null || ctx.relOp != null) {
            // two operands
            final var lhs = visit(ctx.exp(0));
            final var rhs = visit(ctx.exp(1));

            if (ctx.arithOp != null) {
                if (isConst(lhs) && isConst(rhs)) {
                    return evaluate(ctx.arithOp.getText(), (IceConstantData) lhs, (IceConstantData) rhs);
                }
                Tool.TODO();
            }

            if (ctx.logicOp != null) {
                if (isConst(lhs) && isConst(rhs)) {
                    return evaluate(ctx.logicOp.getText(), (IceConstantData) lhs, (IceConstantData) rhs);
                }
                Tool.TODO();
            }

            if (ctx.relOp != null) {
                if (isConst(lhs) && isConst(rhs)) {
                    return evaluate(ctx.relOp.getText(), (IceConstantData) lhs, (IceConstantData) rhs);
                }
                Tool.TODO();
            }
        }

        return super.visitExp(ctx);
    }

    @Override
    public IceConstantData visitNumber(SysyParser.NumberContext ctx) {
        final var literal = ctx.getText();
        if (ctx.IntConst() != null) {
            return IceConstantData.create(null, Long.parseLong(literal));
        } else if (ctx.FloatConst() != null) {
            return IceConstantData.create(null, Double.parseDouble(literal));
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
    public IceValue visitFuncCall(SysyParser.FuncCallContext ctx) {
        Tool.TODO();
        return null;
    }

    public IceValue fetchConstValue(IceConstantData target, SysyParser.LValContext ctx) {
        if (target instanceof IceConstantDataArray) {
            Log.should(!ctx.exp().isEmpty(), "Array access should have one index");
            final var arrayRefValues = ctx.exp().stream().map(this::visit).toList();
            if (arrayRefValues.stream().anyMatch(exp -> !isConst(exp))) {
                Tool.TODO(); // generate code
            }
            final var constArrayRef = arrayRefValues.stream()
                    .map(exp -> (int) ((IceConstantInt) exp).getValue()).toList();
            return ((IceConstantDataArray) target).get(constArrayRef).clone();
        } else {
            return target.clone();
        }
    }

    @Override
    public IceValue visitLVal(SysyParser.LValContext ctx) {
        final var target = ctx.Ident().getText();
        final var symbol = SymbolTable.current().get(target);

        if (symbol instanceof IceConstantData) {
            return fetchConstValue((IceConstantData) symbol, ctx);
        } else {
            // variable generate code
            Tool.TODO();
            return null;
        }
    }
}
