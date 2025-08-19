package top.voidc.misc.scev;

import top.voidc.misc.Log;
import top.voidc.misc.exceptions.SCEVEvaluateException;

public class SCEVIntConst extends SCEVConst{

    private final int value;

    public SCEVIntConst(int value) {
//        Log.d("创建了int常量：" + value);
        this.value = value;
    }

    @Override
    public SCEVValue merge(SCEVValue expression) {

        if (expression instanceof SCEVIntConst intConst) {
            return new SCEVIntConst(value + intConst.getValue());

        } else if (expression instanceof SCEVFloatConst floatConst) {
            return new SCEVFloatConst(value + floatConst.getValue());

        } else {
            try {
                return super.merge(expression);
            } catch (SCEVEvaluateException e) {
                throw new SCEVEvaluateException("未实现的合并（相加）操作: ");
            }
        }

    }

    @Override
    public SCEVValue multi(SCEVValue expression) {
        if (expression instanceof SCEVIntConst intConst) {
            return new SCEVIntConst(value * intConst.getValue());

        } else if (expression instanceof SCEVFloatConst floatConst) {
            return new SCEVFloatConst(value * floatConst.getValue());

        } else {
            try {
                return super.multi(expression);
            } catch (SCEVEvaluateException e) {
                throw new SCEVEvaluateException("未实现的相乘操作: ");
            }
        }
    }

    public int getValue() {
        return value;
    }

    @Override
    public String toString(){
        return value + "d";
    }
}
