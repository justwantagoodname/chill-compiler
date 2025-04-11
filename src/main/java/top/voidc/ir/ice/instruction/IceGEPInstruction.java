package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.misc.Log;

import java.util.List;

public class IceGEPInstruction extends IceInstruction {
    private boolean isInBounds = false;

    public IceGEPInstruction(IceBlock block, IceValue basePtr, List<IceValue> indices) {
        super(block, basePtr.getType());
        setInstructionType(InstructionType.GEP);
        Log.should(basePtr.getType().isPointer(), "GEP指令的基址必须是指针类型");
        this.addOperand(basePtr);
        indices.forEach(this::addOperand);
    }

    public IceValue getBasePtr() {
        return this.getOperand(0);
    }

    public boolean isInBounds() {
        return isInBounds;
    }

    public void setInBounds(boolean inBounds) {
        isInBounds = inBounds;
    }
}
