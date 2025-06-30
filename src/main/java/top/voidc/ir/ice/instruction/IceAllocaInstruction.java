package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;

public class IceAllocaInstruction extends IceInstruction {
    public IceAllocaInstruction(IceBlock parent, String name, IceType type) {
        super(parent, name, new IcePtrType<>(type));

    }

    @Override
    public IcePtrType<?> getType() {
        return (IcePtrType<?>) super.getType();
    }

    public IceAllocaInstruction(IceBlock parent, IceType type) {
        super(parent, parent.getFunction().generateLocalValueName(), new IcePtrType<>(type));

    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = alloca ")
                .append(getType().getPointTo());
    }
}
