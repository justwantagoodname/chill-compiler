package top.voidc.misc.scev;

import top.voidc.misc.Log;

import java.util.List;
import java.util.Map;

// TODO
public class SCEVAddRec extends SCEVRec {

    public SCEVAddRec(SCEVValue start, SCEVValue step, String iter){
        super(start, step, iter);
    }

    public SCEVAddRec(SCEVValue start, SCEVValue step, SCEVLoopIterator iter){
        super(start, step, iter);
    }

    @Override
    public SCEVValue evaluate(Map<String, LoopInfo> context) {
        return start.evaluate(context).merge(step.evaluate(context).multi(context.get(iter.loop).times));
    }

    @Override
    public SCEVValue simplify() {
        var steped = step.simplify();
        var started = start.simplify();

        // 合并start
        if(started instanceof SCEVAddRec addRec && addRec.iter.equals(iter)){
            started = addRec.start.merge(started);
        }

        if(steped instanceof SCEVAddRec addRec && addRec.iter.equals(iter)){
            // 如果是等差（step为常熟），转化为等差数列求和
            if (addRec.step instanceof SCEVConst d){
                Log.d(this + "是等差数列！");
                return new SCEVWhile(
                        started,
                        new SCEVAdd(
                                new SCEVMultiply(
                                        new SCEVFloatConst(0.5f),
                                        d,
                                        iter,
                                        iter
                                ),
                                new SCEVMultiply(
                                        addRec.start,
                                        iter
                                ),
                                new SCEVMultiply(
                                        new SCEVFloatConst(-0.5f),
                                        d,
                                        iter
                                )
                        ),
                        iter);
            }
        }

//        throw new SCEVEvaluateException("未实现！");
        return new SCEVAddRec(started, steped, iter);
    }

    @Override
    public List<SCEVValue> getOperands() {
        return List.of(start, step);
    }

    @Override
    public SCEVValue merge(SCEVValue expression) {
        return new SCEVAddRec(start.merge(expression), step, iter);
    }

    @Override
    public SCEVValue multi(SCEVValue expression) {
        return new SCEVAddRec(start.multi(expression), step.multi(expression), iter);

    }

    @Override
    public String toString(){
        return "{" + start + ", +, " + step + "}" + iter;
    }
}
