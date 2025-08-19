package top.voidc.misc.scev;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;

import java.util.List;
import java.util.Map;

public abstract class SCEVRec extends SCEVValue{

    protected final SCEVValue start;
    protected final SCEVValue step;
    protected final SCEVLoopIterator iter;

    public SCEVRec(SCEVValue start, SCEVValue step, String iter) {
        this.start = start;
        this.step = step;
        this.iter = new SCEVLoopIterator(iter);
    }

    public SCEVRec(SCEVValue start, SCEVValue step, SCEVLoopIterator iter) {
        this.start = start;
        this.step = step;
        this.iter = iter;
    }



//    @Override
//    SCEVValue evaluate(Map<String, LoopInfo> context) {
//        return null;
//    }

//    @Override
//    SCEVValue simplify() {
//        var simpledStart = start.simplify();
//        var simpledBody = body.simplify();
//        var simpledLoop = loop.simplify();
//
//        if (simpledBody instanceof SCEVRec scevWhile) {
//            return new SCEVRec(new SCEVRec(simpledStart, scevWhile.start, simpledLoop), scevWhile.body, simpledLoop.multi(scevWhile.loop));
//        } else if (simpledBody instanceof SCEVAddRec scevAddRec){
//            return new SCEVRec(new SCEVRec(simpledStart, scevAddRec., ))
//        }
//
//    }

    @Override
    List<SCEVValue> getOperands() {
        return List.of(start, step, iter);
    }

}
