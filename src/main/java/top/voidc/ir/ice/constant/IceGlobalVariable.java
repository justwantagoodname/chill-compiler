package top.voidc.ir.ice.constant;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceGlobalVariable extends IceConstant {

    private IceConstantData initializer;

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
        builder.append(getReferenceName()).append(" = ")
                .append(getType().asPointer().isConst() ? "constant " : "global ");
        if (getInitializer() != null) {
            getInitializer().getTextIR(builder);
        } else {
            builder.append(getType()).append(" zeroinitializer");
        }
    }
}
