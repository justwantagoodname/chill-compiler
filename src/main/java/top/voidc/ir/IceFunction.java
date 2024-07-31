package top.voidc.ir;

import top.voidc.ir.type.IceType;

import java.util.ArrayList;
import java.util.List;

public class IceFunction extends IceConstant {
    private final List<IceType> parameterTypes;
    private IceType returnType;

    private final List<IceValue> parameters;

    public IceFunction(String name) {
        super(name, IceType.FUNCTION);
        this.parameterTypes = new ArrayList<>();
        this.parameters = new ArrayList<>();
    }

    public void setReturnType(IceType returnType) {
        this.returnType = returnType;
    }

    public IceType getReturnType() {
        return returnType;
    }

    public List<IceType> getParameterTypes() {
        return parameterTypes;
    }

    public List<IceValue> getParameters() {
        return parameters;
    }

    public void addParameter(IceValue parameter) {
        parameters.add(parameter);
        parameterTypes.add(parameter.getType());
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("define ").append(returnType).append(" @").append(getName()).append("(");
        sb.append(String.join(", ", parameters.stream()
                            .map(p -> p.getType() + " %" + p.getName()).toArray(String[]::new)));
        sb.append(')').append(" {").append('}');
        return sb.toString();
    }
}
