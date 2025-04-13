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
}
