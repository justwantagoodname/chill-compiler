package top.voidc.misc.scev;

import top.voidc.misc.Log;
import top.voidc.misc.exceptions.SCEVEvaluateException;

import java.util.Map;

public class SCEVWhile extends SCEVRec{

    public SCEVWhile(SCEVValue start, SCEVValue step, String iter){
        super(start, step, iter);
    }

    public SCEVWhile(SCEVValue start, SCEVValue step, SCEVLoopIterator iter){
        super(start, step, iter);
    }

    @Override
    public SCEVValue evaluate(Map<String, LoopInfo> context) {
        context.put(iter.loop, new LoopInfo(context.get(iter.loop).times, true));
        return start.merge(step.evaluate(context)).evaluate(context);
    }

    @Override
    public SCEVValue simplify() {
        var simpledStart = start.simplify();
        var simpledBody = step.simplify();

        if (simpledBody instanceof SCEVRec scevWhile) {
            return new SCEVWhile(new SCEVWhile(simpledStart, scevWhile.start, iter), scevWhile.step, iter);
        } else if (simpledBody instanceof SCEVAddRec scevAddRec){
//            return new SCEVWhile(new SCEVWhile(simpledStart, scevAddRec.start, iter), )
        }
        throw new SCEVEvaluateException("while中没有实现的功能！");
    }

    @Override
    public String toString() {
        return start + " + while {" + step + "}" + iter;
    }
}
