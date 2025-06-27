package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.List;
import java.util.ArrayList;

/**
 * Update 将分支和值均作为操作数
 * 按照(block, value), (block, value)的顺序存储
 */
public class IcePHINode extends IceInstruction {
    public record IcePHIBranch(IceBlock block, IceValue value) {}

    private IceValue valueToBeMerged;

    public IcePHINode(IceBlock parent, String name, IceType type) {
        super(parent, name, type);
    }

    public List<IcePHIBranch> getBranches() {
        final var operands = getOperands();
        assert operands.size() % 2 == 0;
        List<IcePHIBranch> branches = new ArrayList<>();
        for (int i = 0; i < operands.size(); i += 2) {
            var block = (IceBlock) operands.get(i);
            var value = operands.get(i + 1);
            branches.add(new IcePHIBranch(block, value));
        }
        return branches;
    }

    public void addBranch(IceBlock block, IceValue value) {
        addOperand(block);
        addOperand(value);
    }

    /**
     * 从 PHI 节点中删除所有经过 block 的定值
     * 若删除之后只剩下一个操作数，则将其替换为当前节点的所有使用者
     * @param block 要删除的分支
     */
    public void removeValueByBranch(IceBlock block) {
        assert getOperands().size() % 2 == 0;

        if (getOperands().size() % 2 != 0) {
            Log.d("Name: " + this.getName() + "Param Size: " + this.getOperands().size());
            throw new RuntimeException("Fuck you");
        }

        final var iterator = getOperands().iterator();
        while (iterator.hasNext()) {
            var operand = iterator.next();
            if (operand.equals(block)) {
                operand.removeUse(this);
                iterator.remove();
                iterator.next().removeUse(this); // remove the value
                iterator.remove();
            }
        }

        removeCheck();
    }

    /**
     * 移除某个值（注意不是分支，移除分支请用remove）
     * @param value 要移除的值
     */
    private void removeValue(IceValue value) {
        assert getOperands().size() % 2 == 0;

        final var iterator = getOperands().listIterator();
        while (iterator.hasNext()) {
            var operand = iterator.next();
            if (operand.equals(value)) {
                operand.removeUse(this);
                iterator.remove();
                iterator.previous().removeUse(this); // remove the value
                iterator.remove();
            }
        }

        removeCheck();
    }

    private void removeCheck() {
        // 只剩一个值了，直接用这个值替换自身
        assert getOperands().size() % 2 == 0;
        if (getOperands().size() / 2 == 1) {
            replaceAllUsesWith(getBranchValueOnIndex(0));

            // 清空对最后的分支和值的使用
            final var lastIter = getOperands().iterator();
            while (lastIter.hasNext()) {
                lastIter.next().removeUse(this);
                lastIter.remove();
            }
        }
        this.destroy();
    }

    /**
     * 获取 block 对应的值
     */
    public IceValue getIncomingValue(IceBlock block) {
        assert getOperands().size() % 2 == 0;
        for (var i = 0; i < getOperands().size(); i += 2) {
            var b = (IceBlock) getOperands().get(i);
            if (b == block) {
                return getOperands().get(i + 1);
            }
        }

        throw new RuntimeException("PHI node does not have incoming value for block: " + block.getName());
    }

    /**
     * 获取对应组下标分支的值，每两个算作一组
     * @param index 组下标
     * @return 对应组下标的值
     */
    public IceValue getBranchValueOnIndex(int index) {
        assert getOperands().size() % 2 == 0;
        if (index < 0 || index * 2 >= getOperands().size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + getOperands().size());
        }
        return getOperands().get(index * 2 + 1);
    }

    public boolean containsBranch(IceBlock branch) {
        assert getOperands().size() % 2 == 0;
        for (var i = 0; i < getOperands().size(); i += 2) {
            var b = (IceBlock) getOperands().get(i);
            if (b.equals(branch)) {
                return true;
            }
        }

        return false;
    }

    public int getBranchCount() {
        assert getOperands().size() % 2 == 0;
        return getOperands().size() / 2;
    }

    public IceValue getValueToBeMerged() {
        return valueToBeMerged;
    }

    public void setValueToBeMerged(IceValue valueToBeMerged) {
        this.valueToBeMerged = valueToBeMerged;
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = phi ").append(getType()).append(" ");
        final var branches = getBranches();
        for (int i = 0; i < branches.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }

            IceBlock branch = branches.get(i).block();
            IceValue value = branches.get(i).value();
            builder.append("[ ").append(value.getReferenceName(false)).append(", ").append(branch.getReferenceName(false)).append(" ]");
        }
    }

    @Override
    public void replaceOperand(IceValue oldOperand, IceValue newOperand) {
        if (newOperand == null) {
            // 删除操作数的情况，必须要成对删除
            if (oldOperand instanceof IceBlock branch) {
                this.removeValueByBranch(branch); // 同步删除分支和值
            } else {
                this.removeValue(oldOperand);
            }
        } else {
            super.replaceOperand(oldOperand, newOperand);
        }
    }
}
