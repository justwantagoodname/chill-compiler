package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.ice.type.IceType;

public abstract class IceInstruction extends IceUser {
    public IceBlock getParent() {
        return parent;
    }

    /**
     * 将当前指令移动到新的父节点
     * @apiNote 如果需要移动一整个块的话，需要复制一份instructions的副本操作
     * @param parent 新的父节点
     */
    public void moveTo(IceBlock parent) {
        if (this.parent != null) {
            this.parent.remove(this);
        }
        this.parent = parent;
        if (parent != null) {
            parent.addInstruction(this);
        }
    }

    /**
     * 删除当前指令，迭代时删除请用block迭代器的remove方法
     * @apiNote 由父节点调用
     */
    @Override
    public void destroy() {
        if (parent != null) {
            parent.remove(this);
            parent = null;
        }
        super.destroy();
    }

    /**
     * 设置父节点，如果只是想要移动指令
     * 不要直接调用这个方法应该使用{@link #moveTo(IceBlock)}
     * @param parent 新的父节点
     */
    public void setParent(IceBlock parent) {
        this.parent = parent;
    }

    private IceBlock parent;

    public IceInstruction(IceBlock parent, String name, IceType type) {
        super(name, type);
        this.parent = parent;
    }

    public IceInstruction(IceBlock parent, IceType type) {
        super(type.isVoid() ? null : parent.getFunction().generateLocalValueName(), type);
        this.parent = parent;
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        String instructionName = switch (this) {
            case IceUnreachableInstruction _ -> "unreachable";
            case IceIntrinsicInstruction _ -> "intrinsic";
            case IceBranchInstruction _ -> "br";
            case IceCmpInstruction _ -> "cmp";
            case IceRetInstruction _ -> "ret";
            case IceGEPInstruction _ -> "getelementptr";
            case IceCallInstruction _ -> "call";
            case IceConvertInstruction _ -> "tconvert";
            case IceAllocaInstruction _ -> "alloca";
            case IceLoadInstruction _ -> "load";
            case IceStoreInstruction _ -> "store";
            case IceBinaryInstruction.Add _ -> "add";
            case IceBinaryInstruction.FAdd _ -> "fadd";
            case IceBinaryInstruction.Sub _ -> "sub";
            case IceBinaryInstruction.FSub _ -> "fsub";
            case IceBinaryInstruction.Mul _ -> "mul";
            case IceBinaryInstruction.FMul _ -> "fmul";
            case IceBinaryInstruction.Div _ -> "div";
            case IceBinaryInstruction.SDiv _ -> "sdiv";
            case IceBinaryInstruction.FDiv _ -> "fdiv";
            case IceBinaryInstruction.Mod _ -> "srem";
            case IceBinaryInstruction.Shl _ -> "shl";
            case IceBinaryInstruction.Shr _ -> "shr";
            case IceBinaryInstruction.And _ -> "and";
            case IceBinaryInstruction.Or _ -> "or";
            case IceBinaryInstruction.Xor _ -> "xor";
            case IceNegInstruction _ -> "neg";
            case IcePHINode _ -> "phi";
            default -> throw new IllegalStateException("Unexpected instruction type: " + this.getClass().getSimpleName());
        };
        builder.append(instructionName);
    }

    public boolean isTerminal() {
        return this instanceof IceBranchInstruction || this instanceof IceRetInstruction || this instanceof IceUnreachableInstruction;
    }

    @Override
    public String toString() {
        return getTextIR();
    }
}
