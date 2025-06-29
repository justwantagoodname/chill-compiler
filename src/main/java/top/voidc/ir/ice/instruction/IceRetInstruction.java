package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.Optional;

public class IceRetInstruction extends IceInstruction {

    public IceRetInstruction(IceBlock parent, IceValue retValue) {
        super(parent, IceType.VOID);
        addOperand(retValue);
    }

    public IceRetInstruction(IceBlock parent) {
        super(parent, IceType.VOID);
    }

    public Optional<IceValue> getReturnValue() {
        return Optional.ofNullable(getOperand(0));
    }

    public boolean isReturnVoid() {
        return getOperandsList().isEmpty();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("ret ");
        if (isReturnVoid()) {
            builder.append("void");
        } else {
            var retValue = getReturnValue().get();
            builder.append(retValue.getReferenceName());
        }
    }

    @Override
    public String toString() {
        return getTextIR();
    }
}
