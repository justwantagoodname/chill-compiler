package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.List;
import java.util.Objects;

public class IceInstruction extends IceUser {
    public IceBlock getParent() {
        return parent;
    }

    /**
     * 将当前指令移动到新的父节点
     *
     * @param parent 新的父节点
     */
    public void moveTo(IceBlock parent) {
        if (this.parent != null) {
            this.parent.removeInstruction(this);
        }
        this.parent = parent;
        if (parent != null) {
            parent.addInstruction(this);
        }
    }

    public void destroy() {
        if (parent != null) {
            parent.removeInstruction(this);
            parent = null;
        }
        for (IceValue operand : getOperandsList()) {
            operand.removeUse(this);
        }
    }

    public enum InstructionType {
        UNREACHABLE,
        INTRINSIC,
        BRANCH,
        CMP,
        GEP,
        CALL,
        TCONVERT,
        ALLOCA,
        LOAD,
        STORE,
        BINARY,
        NEG,
        ADD,
        FADD,
        SUB,
        FSUB,
        MUL,
        FMUL,
        DIV,
        SDIV,
        FDIV,
        MOD,
        SHL,
        SHR,
        AND,
        OR,
        XOR,
        RET,
        PHI;

        @Override
        public String toString() {
            return switch (this) {
                case UNREACHABLE -> "unreachable";
                case INTRINSIC -> "intrinsic";
                case BRANCH -> "br";
                case CMP -> "cmp";
                case RET -> "ret";
                case GEP -> "getelementptr";
                case CALL -> "call";
                case TCONVERT -> "tconvert";
                case ALLOCA -> "alloca";
                case LOAD -> "load";
                case STORE -> "store";
                case BINARY -> "binary";
                case ADD -> "add";
                case FADD -> "fadd";
                case SUB -> "sub";
                case FSUB -> "fsub";
                case MUL -> "mul";
                case FMUL -> "fmul";
                case DIV -> "div";
                case SDIV -> "sdiv";
                case FDIV -> "fdiv";
                case MOD -> "srem";
                case SHL -> "shl";
                case SHR -> "shr";
                case AND -> "and";
                case OR -> "or";
                case XOR -> "xor";
                case NEG -> "neg";
                case PHI -> "phi";
            };
        }

        public static InstructionType fromSysyLiteral(String str) {
            str = Objects.requireNonNull(str);
            return switch (str) {
                case "+" -> InstructionType.ADD;
                case "-" -> InstructionType.SUB;
                case "*" -> InstructionType.MUL;
                case "/" -> InstructionType.DIV;
                case "%" -> InstructionType.MOD;
                case "&" -> InstructionType.AND;
                case "|" -> InstructionType.OR;
                default -> throw new IllegalStateException("Unexpected value: " + str);
            };
        }
    }

    private IceBlock parent;
    InstructionType type;

    public IceInstruction(IceBlock parent, String name, IceType type) {
        super(name, type);
        this.parent = parent;
    }

    public IceInstruction(IceBlock parent, IceType type) {
        super(type.isVoid() ? null : parent.getFunction().generateLocalValueName(), type);
        this.parent = parent;
    }

    public InstructionType getInstructionType() {
        return this.type;
    }

    protected void setInstructionType(InstructionType type) {
        this.type = type;
    }

    public void replaceOperand(IceValue oldOperand, IceValue newOperand) {
        if (oldOperand == null || newOperand == null) {
            throw new IllegalArgumentException("Operands cannot be null");
        }
        if (oldOperand == newOperand) {
            return;
        }
        if (oldOperand.getType() != newOperand.getType()) {
            throw new IllegalArgumentException("Operands must have the same type");
        }

        List<IceValue> operands = getOperandsList();
        for (int i = 0; i < operands.size(); ++i) {
            if (operands.get(i) == oldOperand) {
                operands.set(i, newOperand);
                oldOperand.removeUse(this);
                newOperand.addUse(this);
                return;
            }
        }

        throw new IllegalArgumentException("Old operand not found in instruction");
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(getInstructionType());
    }
}
