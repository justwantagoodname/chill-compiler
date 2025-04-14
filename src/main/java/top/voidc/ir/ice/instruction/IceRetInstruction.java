package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.Optional;

public class IceRetInstruction extends IceInstruction {

    public IceRetInstruction(IceBlock parent, IceValue retValue) {
        super(parent, retValue.getType());
        setInstructionType(InstructionType.RET);
        addOperand(retValue);
    }

    public IceRetInstruction(IceBlock parent) {
        super(parent, IceType.VOID);
        setInstructionType(InstructionType.RET);
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
}
