package top.voidc.misc.scev;

import top.voidc.misc.Log;
import top.voidc.misc.exceptions.SCEVEvaluateException;

import java.util.*;

public abstract class SCEVValue {
    public static class LoopInfo{
        public SCEVValue times;
        public boolean ifend;

        public LoopInfo(int times){
            this.times = new SCEVIntConst(times);
            this.ifend = false;
        }

        public LoopInfo(SCEVValue times){
            this.times = times;
            this.ifend = false;
        }

        public LoopInfo(SCEVValue times, boolean ifend){
            this.times = times;
            this.ifend = ifend;
        }
    }

    /**
     * 求值表达式
     * @return 计算结果
     */
    abstract SCEVValue evaluate(Map<String, LoopInfo> context);

    /**
     * 简化表达式（主要是将循环表达式外提）
     * @return 简化后的表达式
     */
    abstract SCEVValue simplify();

    /**
     * 获取子表达式
     */
    abstract List<SCEVValue> getOperands();

    private boolean flag = false;
    /**
     * 相加，请尽量实现与其他每个类型的相加，需要反向相加请调用super
     */
    public SCEVValue merge(SCEVValue expression){
        if(this.flag){
//            throw new SCEVEvaluateException("Error: 递归相加");
            Log.w("Error: [" + expression + "] * [" + this + "] 中发生递归相加");
            this.flag = false;
            return new SCEVAdd(this, expression);
        }
        flag = true;
        final var ans = expression.merge(this);
        flag = false;
        return ans;
    }

    /**
     * 相乘，请尽量实现与其他每个类型的相乘，需要反向相乘请调用super
     */
    public SCEVValue multi(SCEVValue expression){
        if(this.flag){
//            throw new SCEVEvaluateException();
            Log.w("Error: [" + expression + "] * [" + this + "] 中发生递归相乘");
            this.flag = false;
            return new SCEVMultiply(this, expression);
        }
        flag = true;
        final var ans = expression.multi(this);
        flag = false;
        return ans;
    }

    @Override
    public String toString(){
        return "[a undefined value]";
    }
}
