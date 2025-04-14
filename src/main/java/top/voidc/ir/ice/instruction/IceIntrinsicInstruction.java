package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.List;

public class IceIntrinsicInstruction extends IceInstruction {
    public static final String MEMSET = "llvm.memset.p0.i32";
    public static final String MEMCPY = "llvm.memcpy.p0.i32";

    private final String intrinsicName;

    public IceIntrinsicInstruction(
            IceBlock parent,
            String name,
            IceType retType,
            String intrinsicName,
            List<IceValue> parameters) {
        super(parent, name, retType);
        setInstructionType(InstructionType.INTRINSIC);
        this.intrinsicName = intrinsicName;
        parameters.forEach(this::addOperand);
    }
    public IceIntrinsicInstruction(
            IceBlock parent,
            IceType retType,
            String intrinsicName,
            List<IceValue> parameters) {
        super(parent, parent.getFunction().generateLocalValueName(), retType);
        setInstructionType(InstructionType.INTRINSIC);
        this.intrinsicName = intrinsicName;
        parameters.forEach(this::addOperand);
    }

    public String getIntrinsicName() {
        return intrinsicName;
    }

    public List<IceValue> getParameters() {
        return getOperandsList();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        if (!getType().isVoid()) {
            builder.append("%").append(getName()).append(" = ");
        }
        builder.append("call ").append(getType()).append(" @")
                .append(intrinsicName).append("(");
        
        var params = getParameters();
        for (int i = 0; i < params.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(params.get(i).getReferenceName());
        }
        builder.append(")");
    }
}
