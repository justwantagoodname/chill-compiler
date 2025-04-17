package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceGlobalVariable extends IceConstant {

    private IceConstantData initializer;
    private boolean isPrivate = false;
    private boolean isUnnamedAddr = false;


    public IceGlobalVariable(String name, IceType type, IceConstantData initializer) {
        super(name, new IcePtrType<>(type));
        this.initializer = initializer;
    }

    public IceConstantData getInitializer() {
        return initializer;
    }

    public void setInitializer(IceConstantData initializer) {
        this.initializer = initializer;
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getReferenceName(false)).append(" = ")
                .append(getType().asPointer().isConst() ? "constant " : "global ");
        if (getInitializer() != null) {
            getInitializer().getTextIR(builder);
        } else {
            builder.append(((IcePtrType<?>) getType()).getPointTo()).append(" zeroinitializer");
        }
    }

    public boolean isPrivate() {
        return isPrivate;
    }

    public void setPrivate(boolean aPrivate) {
        isPrivate = aPrivate;
    }

    public boolean isUnnamedAddr() {
        return isUnnamedAddr;
    }

    public void setUnnamedAddr(boolean unnamedAddr) {
        isUnnamedAddr = unnamedAddr;
    }
}
