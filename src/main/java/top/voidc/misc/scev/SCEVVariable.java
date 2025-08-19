package top.voidc.misc.scev;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IcePHINode;
import top.voidc.misc.exceptions.SCEVEvaluateException;

import java.util.Map;

public class SCEVVariable extends SCEVConst{

    public IceValue variable;

    public SCEVVariable(IceValue iceValue) {
        this.variable = iceValue;
    }

    @Override
    public String toString(){
        return "[var " + variable + "]";
    }

    public IceValue getVariable() {
        return variable;
    }
}
