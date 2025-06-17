package top.voidc.ir.ice.constant;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.List;

public class IceExternFunction extends IceFunction {
    private boolean isVArgs = false;

    public IceExternFunction(String name) {
        super(name);
    }

    public boolean isVArgs() {
        return isVArgs;
    }

    public void setVArgs(boolean VArgs) {
        isVArgs = VArgs;
    }

    @Override
    public String getReferenceName(boolean withType) {
        return "@" + getName() + "(" +
                String.join(", ",
                        getParameters().stream()
                                .map(IceValue::getType)
                                .map(IceType::toString)
                                .toList())
                + (isVArgs() ? ", ...)" : ")");
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("declare ")
                .append(getReturnType())
                .append(" ").append(getReferenceName());
    }
}
