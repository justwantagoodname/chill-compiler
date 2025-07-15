package top.voidc.ir.machine;


import top.voidc.ir.ice.interfaces.IceAlignable;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;

public class IceStackSlot extends IceValue implements IceAlignable, IceMachineValue {
    private final IceMachineFunction parent;
    private int offset = -1; // 距离栈顶的偏移量
    private boolean isFinalized = false; // 是否已经分配了偏移量
    private int alignment;

    public enum StackSlotType {
        VARIABLE, // 普通变量
        ARGUMENT, // 函数实参
    }

    private StackSlotType stackSlotType;


    public IceStackSlot(IceMachineFunction parent, IceType type, StackSlotType stackSlotType) {
        super(null, type);
        this.parent = parent;
        this.stackSlotType = stackSlotType;
    }


    public StackSlotType getStackSlotType() {
        return stackSlotType;
    }

    public void setStackSlotType(StackSlotType stackSlotType) {
        if (isFinalized) {
            throw new IllegalStateException("Cannot change stack slot type after it has been finalized.");
        }
        this.stackSlotType = stackSlotType;
    }

    public int getOffset() {
        if (!isFinalized) {
            throw new IllegalStateException("Stack slot offset is not finalized yet.");
        }
        return offset;
    }

    public void setOffset(int offset) {
        if (isFinalized) {
            throw new IllegalStateException("Stack slot offset has already been finalized.");
        }
        this.offset = offset;
        this.isFinalized = true; // 设置为已分配状态
    }

    public IceMachineFunction getParent() {
        return parent;
    }

    @Override
    public String getReferenceName(boolean withType) {
        // TODO 根据平台重写
        return getName() + (isFinalized ? ", offset=" + offset : "uninitialized");
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        if (!isFinalized) builder.append("stack_slot(").append(getName()).append("uninitialized)");
            else builder.append("stack_slot(").append(getName()).append(", offset=").append(offset).append(")");
    }

    @Override
    public int getAlignment() {
        return alignment;
    }

    @Override
    public void setAlignment(int alignment) {
        this.alignment = alignment;
    }
}
