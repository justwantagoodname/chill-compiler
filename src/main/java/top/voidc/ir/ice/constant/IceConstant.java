package top.voidc.ir.ice.constant;

import top.voidc.ir.IceUser;
import top.voidc.ir.ice.type.IceType;

public class IceConstant extends IceUser {

    public IceConstant(String name, IceType type) {
        super(name, type);
    }


    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " @" : "@") + getName();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getReferenceName());
    }
}
