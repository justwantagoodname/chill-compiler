package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IceType;

public class IceGlobalVariable extends IceConstant {

    private IceConstantData initializer;

    public IceGlobalVariable(String name, IceType type, IceConstantData initializer) {
        super(name, type);
        this.initializer = initializer;
    }

    @Override
    public String toString() {
        if (initializer != null)
            return String.format("%%%s = global %s %s", getName(), getType(), initializer);
        else return String.format("%%%s = global %s", getName(), getType());
    }

    public IceConstantData getInitializer() {
        return initializer;
    }

    public void setInitializer(IceConstantData initializer) {
        this.initializer = initializer;
    }
}
