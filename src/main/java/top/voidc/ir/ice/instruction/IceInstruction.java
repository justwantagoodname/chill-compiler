package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.ice.type.IceType;

public abstract class IceInstruction extends IceUser implements Cloneable {
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

    public void moveTo(IceBlock parent, int index) {
        if (this.parent != null) {
            this.parent.remove(this);
        }
        this.parent = parent;
        if (parent != null) {
            parent.add(index, this);
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

    public boolean isTerminal() {
        return false;
    }

    @Override
    public String toString() {
        return getTextIR();
    }

    @Override
    public IceInstruction clone() {
        IceInstruction clone = (IceInstruction) super.clone();
        // parent初始化为null，需要调用者在适当时机设置
        clone.parent = null;
        return clone;
    }
}
