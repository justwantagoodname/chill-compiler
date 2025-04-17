package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.List;
import java.util.ArrayList;

public class IcePHINode extends IceInstruction {
    record IcePHIBranch(IceBlock block, IceValue value) {};
    private List<IcePHIBranch> branches;
    private IceValue valueToBeMerged;

    public IcePHINode(IceBlock parent, String name, IceType type) {
        super(parent, name, type);
        setInstructionType(InstructionType.PHI);
        this.branches = new ArrayList<>();
    }

    public void addBranch(IceBlock block, IceValue value) {
        branches.add(new IcePHIBranch(block, value));
    }

    public IceValue getIncomingValue(IceBlock block) {
        for (IcePHIBranch b : branches) {
            if (b.block() == block) {
                return b.value();
            }
        }

        throw new RuntimeException("PHI node does not have incoming value for block: " + block.getName());
    }

    public boolean containsBranch(IceBlock branch) {
        for (IcePHIBranch b : branches) {
            if (b.block() == branch) {
                return true;
            }
        }

        return false;
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
}
