package top.voidc.misc.scev;

import java.util.List;
import java.util.Map;

public abstract class SCEVConst extends SCEVValue {

    @Override
    public List<SCEVValue> getOperands() { return List.of(); }

    @Override
    public SCEVValue evaluate(Map<String, LoopInfo> context) { return this; }

    @Override
    public SCEVValue simplify() { return this; }

    @Override
    public String toString(){
        return "[a undefine const value]";
    }

}
