package top.voidc.ir.machine;


import top.voidc.ir.IceUser;
import top.voidc.ir.ice.instruction.IceCallInstruction;
import top.voidc.ir.ice.interfaces.IceAlignable;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;

public abstract class IceStackSlot extends IceValue implements IceAlignable, IceMachineValue {
    private final IceMachineFunction parent;
    private int offset = -1; // 距离栈顶的偏移量
    private boolean isFinalized = false; // 是否已经分配了偏移量
    private int alignment;

    public static class VariableStackSlot extends IceStackSlot {
        public VariableStackSlot(IceMachineFunction parent, IceType type) {
            super(parent, type);
            setAlignment(4);
        }
    }

    public static class ArgumentStackSlot extends IceStackSlot {
        private final IceCallInstruction callInstruction;
        private final int argumentIndex; // 参数在调用指令中的索引
        public ArgumentStackSlot(IceMachineFunction parent, IceCallInstruction callInstruction, int argumentIndex, IceType type) {
            super(parent, type);
            this.callInstruction = callInstruction;
            this.argumentIndex = argumentIndex;
        }

        public IceCallInstruction getCallInstruction() {
            return callInstruction;
        }

        public int getArgumentIndex() {
            return argumentIndex;
        }
    }

    @Override
    public void removeUse(IceUser user) {
        super.removeUse(user);
    }

    public IceStackSlot(IceMachineFunction parent, IceType type) {
        super(null, type);
        this.parent = parent;
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
