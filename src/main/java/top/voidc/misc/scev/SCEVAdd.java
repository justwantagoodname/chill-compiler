package top.voidc.misc.scev;

import top.voidc.misc.exceptions.SCEVEvaluateException;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class SCEVAdd extends SCEVExpression {

    public SCEVAdd(SCEVValue... operands) {
        super(operands);
    }

    @Override
    public SCEVValue evaluate(Map<String, LoopInfo> context) {
        SCEVValue sum = new SCEVIntConst(0);
        for (var operand : operands) {
            sum = sum.merge(operand.evaluate(context));
        }
        return sum;
    }


    @Override
    public SCEVValue simplify() {
        SCEVValue sum = new SCEVIntConst(0);
        for (var operand : operands) {
            sum = sum.merge(operand.simplify());
        }
        return sum;
    }

    @Override
    public SCEVValue merge(SCEVValue expression) {
        List<SCEVValue> newAddOps = new ArrayList<>(operands);

        if(expression instanceof SCEVAdd add){
            newAddOps.addAll(add.getOperands());

        } else {
            newAddOps.add(expression);
        }

        return new SCEVAdd(newAddOps.toArray(new SCEVValue[0]));
    }

    @Override
    public SCEVValue multi(SCEVValue expression) {
        throw new SCEVEvaluateException("Add 与其他类型 相乘 未实现");
    }

    @Override
    public String toString(){
        return "(" +
                operands.stream()
                        .map(SCEVValue::toString)
                        .collect(Collectors.joining(" + ")) +
                ")";
    }
}
