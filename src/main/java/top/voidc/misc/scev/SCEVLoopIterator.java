package top.voidc.misc.scev;

import java.util.List;
import java.util.Map;
import java.util.Objects;

public class SCEVLoopIterator extends SCEVValue{

    final String loop;

    public SCEVLoopIterator(String loop) {
        this.loop = loop;
    }

    @Override
    SCEVValue evaluate(Map<String, LoopInfo> context) {
        return context.get(loop).times;
    }

    @Override
    SCEVValue simplify() {
        return this;
    }

    @Override
    List<SCEVValue> getOperands() {
        return List.of();
    }

    @Override
    public String toString() {
        return "<" +  loop + ">";
    }

    public String getLoop(){
        return loop;
    }

    public boolean equals(SCEVLoopIterator iter){
        return Objects.equals(iter.loop, loop);
    }
}
