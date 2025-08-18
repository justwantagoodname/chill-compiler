package top.voidc.misc.scev;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public abstract class SCEVExpression extends SCEVValue {
    protected final List<SCEVValue> operands;

    public SCEVExpression(SCEVValue... operands){
        this.operands = Arrays.asList(operands);
    }

    @Override
    public List<SCEVValue> getOperands() {
        return List.of();
    }

    @Override
    public String toString(){
        return "[a undefined expression] with ( " +
                operands.stream()
                        .map(SCEVValue::toString)
                        .collect(Collectors.joining(", ")) +
        ")";
    }
}
