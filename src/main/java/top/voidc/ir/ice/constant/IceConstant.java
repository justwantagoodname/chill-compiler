package top.voidc.ir.ice.constant;

import top.voidc.ir.IceUser;
import top.voidc.ir.ice.type.IceType;

public class IceConstant extends IceUser {

    public IceConstant(String name, IceType type) {
        super(name, type);
    }

    @Override
    public String getReferenceName() {
        return "@" + getName();
    }

    @Override
    public String toString() {
        return getReferenceName();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getReferenceName());
    }
}
