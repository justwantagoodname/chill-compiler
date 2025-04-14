package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

public class IceStoreInstruction extends IceInstruction {

    public IceStoreInstruction(IceBlock parent, IceValue targetPtr, IceValue value) {
        super(parent, IceType.VOID);
        setInstructionType(InstructionType.STORE);
        Log.should(targetPtr.getType().isPointer(), "store 目标指针类型错误");
        addOperand(targetPtr);
        addOperand(value);
    }

    public IceValue getTargetPtr() {
        return getOperand(0);
    }

    public IceValue getValue() {
        return getOperand(1);
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("store ")
                .append(getValue().getReferenceName())
                .append(", ")
                .append(getTargetPtr().getReferenceName());
    }

}
