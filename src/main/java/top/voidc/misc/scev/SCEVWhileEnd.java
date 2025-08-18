package top.voidc.misc.scev;

import java.util.List;
import java.util.Map;

public class SCEVWhileEnd extends SCEVValue{

    private final SCEVValue body;
    private final String loop;

    public SCEVWhileEnd(SCEVValue body, String loop) {
        this.body = body;
        this.loop = loop;
    }

    @Override
    SCEVValue evaluate(Map<String, LoopInfo> context) {
        return null;
    }

    @Override
    SCEVValue simplify() {
        return null;
    }

    @Override
    List<SCEVValue> getOperands() {
        return List.of();
    }

    @Override
    public SCEVValue merge(SCEVValue expression){
        return null;
    }

    @Override
    public SCEVValue multi(SCEVValue expression){
        return null;
    }

    @Override
    public String toString(){
        return "{" + body + "}<" + loop + ">";
    }
}
