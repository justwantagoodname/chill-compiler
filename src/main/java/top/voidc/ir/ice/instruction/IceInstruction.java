package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceUser;
import top.voidc.ir.ice.type.IceType;

public class IceInstruction extends IceUser {
    public enum InstructionType {
        ALLOCA,
        LOAD,
        STORE,
        BINARY,
        ADD,
        SUB,
        MUL,
        DIV,
        MOD,
        SHL,
        SHR,
        AND,
        OR,
        XOR,
        FADD,
        FSUB,
        FMUL,
        FDIV;

        @Override
        public String toString() {
            return switch (this) {
                case ALLOCA -> "alloca";
                case LOAD -> "load";
                case STORE -> "store";
                case BINARY -> "binary";
                case ADD -> "add";
                case SUB -> "sub";
                case MUL -> "mul";
                case DIV -> "div";
                case MOD -> "mod";
                case SHL -> "shl";
                case SHR -> "shr";
                case AND -> "and";
                case OR -> "or";
                case XOR -> "xor";
                case FADD -> "fadd";
                case FMUL -> "fmul";
                case FSUB -> "fsub";
                case FDIV -> "fdiv";
            };
        }
    }

    InstructionType type;

    public IceInstruction(String name, IceType type) {
        super(name, type);
    }

    public InstructionType getInstructionType() {
        return this.type;
    }

    protected void setInstructionType(InstructionType type) {
        this.type = type;
    }
}
