package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.List;
import java.util.ArrayList;

public class IcePhiInstruction extends IceInstruction {
    record IcePHIBranch(IceBlock block, IceValue value) {};
    private List<IcePHIBranch> branches;
    private IceValue valueToBeMerged;

    public IcePhiInstruction(IceBlock parent, String name, IceType type) {
        super(parent, name, type);
        setInstructionType(InstructionType.PHI);
        this.branches = new ArrayList<>();
    }

    public void addBranch(IceBlock block, IceValue value) {
        super.addOperand(value);
        branches.add(new IcePHIBranch(block, value));
    }

    public void removeBranch(IceBlock block) {
        for (int i = 0; i < branches.size(); ++i) {
            if (branches.get(i).block() == block) {
                super.removeOperand(branches.get(i).value());
                branches.remove(i);
                return;
            }
        }

        throw new RuntimeException("PHI node does not have branch for removing block: " + block.getName());
    }

    public IceValue getIncomingValue(IceBlock block) {
        for (IcePHIBranch b : branches) {
            if (b.block() == block) {
                return b.value();
            }
        }

        throw new RuntimeException("PHI node does not have incoming value for block: " + block.getName());
    }

    public IceValue getBranchValueOnIndex(int index) {
        if (index < 0 || index >= branches.size()) {
            throw new IndexOutOfBoundsException("Index: " + index + ", Size: " + branches.size());
        }
        return branches.get(index).value();
    }

    public boolean containsBranch(IceBlock branch) {
        for (IcePHIBranch b : branches) {
            if (b.block() == branch) {
                return true;
            }
        }

        return false;
    }

    public int getBranchCount() {
        return branches.size();
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
        super.replaceOperand(oldOperand, newOperand);
        for (int i = 0; i < branches.size(); ++i) {
            if (branches.get(i).value() == oldOperand) {
                branches.set(i, new IcePHIBranch(branches.get(i).block(), newOperand));
                return;
            }
        }
    }
}
