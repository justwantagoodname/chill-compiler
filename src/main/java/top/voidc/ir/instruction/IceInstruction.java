package top.voidc.ir.instruction;

import top.voidc.ir.IceUser;
import top.voidc.ir.type.IceType;

public class IceInstruction extends IceUser {
    public enum InstructionType {
        ALLOCA,
        LOAD,
        STORE;

        @Override
        public String toString() {
            return switch (this) {
                case ALLOCA -> "alloca";
                case LOAD -> "load";
                case STORE -> "store";
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
