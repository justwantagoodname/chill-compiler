package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.List;
import java.util.ArrayList;

public class IcePHINode extends IceInstruction {
    record IcePHIBranch(IceBlock block, IceValue value) {};
    private List<IcePHIBranch> branches;

//    public IcePHINode(IceBlock parent, String name, IceType type, List<IceBlock> branches) {
//        super(parent, name, type);
//        setInstructionType(InstructionType.PHI);
//        this.branches = branches;
//    }

    public IcePHINode(IceBlock parent, String name, IceType type) {
        super(parent, name, type);
        setInstructionType(InstructionType.PHI);
        this.branches = new ArrayList<>();
    }

//    public List<IcePHIBranch> getBranches() {
//        return branches;
//    }
    public void addBranch(IceBlock block, IceValue value) {
        branches.add(new IcePHIBranch(block, value));
    }

    public boolean containsBranch(IceBlock branch) {
        for (IcePHIBranch b : branches) {
            if (b.block() == branch) {
                return true;
            }
        }

        return false;
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
            builder.append("[ ").append(value.getReferenceName()).append(", ").append(branch.getReferenceName()).append(" ]");
        }
    }
}
