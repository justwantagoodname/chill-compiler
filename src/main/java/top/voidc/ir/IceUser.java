package top.voidc.ir;

import top.voidc.ir.ice.type.IceType;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class IceUser extends IceValue {
    private final List<IceValue> operands;

    public IceUser() {
        super();
        this.operands = new ArrayList<>();
    }

    public IceUser(String name, IceType type) {
        super(name, type);
        this.operands = new ArrayList<>();
    }

    public void addOperand(IceValue operand) {
        operand.addUse(this);
        operands.add(operand);
    }

    protected void removeAllOperands() {
        operands.forEach(operand -> operand.removeUse(this));
        operands.clear();
    }

    /**
     * 删除操作数
     * @param operand 操作数
     */
    public void removeOperand(IceValue operand) {
        replaceOperand(operand, null);
    }

    /**
     * 用newOperand替换User操作数中**所有**的oldOperand
     * @param oldOperand 原有操作数
     * @param newOperand 新操作数，当新操作数为null时，删除原有操作数
     */
    public void replaceOperand(IceValue oldOperand, IceValue newOperand) {
        Objects.requireNonNull(oldOperand);

        // 或许应该改成 equals 方法？
        if (oldOperand == newOperand) return;

        oldOperand.removeUse(this);

        if (newOperand != null) {
            newOperand.addUse(this);

            // 替换所有等于 oldValue 的元素为 newValue
            operands.replaceAll(e -> e == oldOperand ? newOperand : e);
        } else  {
            // 删除所有等于 oldValue 的元素
            operands.removeIf(e -> e == oldOperand);
        }
    }

    public void setOperand(int index, IceValue operand) {
        if (index < 0 || index >= operands.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + operands.size());
        }
        IceValue oldOperand = operands.get(index);
        if (oldOperand != null) {
            oldOperand.removeUse(this);
        }
        operand.addUse(this);
        operands.set(index, operand);
    }

    public IceValue getOperand(int i) {
        return operands.get(i);
    }

    public List<IceValue> getOperands() {
        return operands;
    }

    public List<IceValue> getOperandsList() {
        return operands;
    }

    @Override
    public void destroy() {
        operands.forEach(operand -> operand.removeUse(this));
        operands.clear();
        super.destroy();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("user with ")
                .append(getOperandsList().size())
                .append(" operand(s)");
    }
}
