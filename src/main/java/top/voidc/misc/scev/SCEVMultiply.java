package top.voidc.misc.scev;

import top.voidc.misc.exceptions.SCEVEvaluateException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SCEVMultiply extends SCEVExpression {
    public SCEVMultiply(SCEVValue... operands){
        super(operands);
    }

    @Override
    public SCEVValue evaluate(Map<String, LoopInfo> context) {
        SCEVValue sum = new SCEVIntConst(1);
        for (var operand : operands) {
            sum = sum.multi(operand.evaluate(context));
        }
        return sum;
    }

    @Override
    public SCEVValue simplify() {
        SCEVValue sum = new SCEVIntConst(1);
        for (var operand : operands) {
            sum = sum.multi(operand.simplify());
        }
        return sum;
    }

    @Override
    public SCEVValue merge(SCEVValue expression) {
        throw new SCEVEvaluateException("暂未实现 Multiply 与 其他 相加 的实现");
    }

    @Override
    public SCEVValue multi(SCEVValue expression) {
        List<SCEVValue> newMulOps = new ArrayList<>(operands);

        if(expression instanceof SCEVMultiply mul){
            newMulOps.addAll(mul.getOperands());

        } else {
            newMulOps.add(expression);
        }

        return new SCEVMultiply(newMulOps.toArray(new SCEVValue[0]));
    }

    @Override
    public String toString(){
        return "(" +
                operands.stream()
                        .map(SCEVValue::toString)
                        .collect(Collectors.joining(" * ")) +
                ")";
    }
}
