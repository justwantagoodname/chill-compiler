package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

/**
 * 二元指令类
 */
public class IceBinaryInstruction extends IceInstruction {
    public static class Add extends IceBinaryInstruction {
        public Add(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Add(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class FAdd extends IceBinaryInstruction {
        public FAdd(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public FAdd(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Sub extends IceBinaryInstruction {
        public Sub(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Sub(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class FSub extends IceBinaryInstruction {
        public FSub(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public FSub(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Mul extends IceBinaryInstruction {
        public Mul(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Mul(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class FMul extends IceBinaryInstruction {
        public FMul(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public FMul(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Div extends IceBinaryInstruction {
        public Div(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Div(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class SDiv extends IceBinaryInstruction {
        public SDiv(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public SDiv(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class FDiv extends IceBinaryInstruction {
        public FDiv(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public FDiv(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Mod extends IceBinaryInstruction {
        public Mod(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Mod(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Shl extends IceBinaryInstruction {
        public Shl(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Shl(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Shr extends IceBinaryInstruction {
        public Shr(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Shr(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class And extends IceBinaryInstruction {
        public And(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public And(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Or extends IceBinaryInstruction {
        public Or(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Or(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }
    
    public static class Xor extends IceBinaryInstruction {
        public Xor(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, type, lhs, rhs);
        }
        
        public Xor(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
            super(parent, name, type, lhs, rhs);
        }
    }

    private boolean isNSW = false;

    protected IceBinaryInstruction(IceBlock parent, IceType type, IceValue lhs, IceValue rhs) {
        super(parent, type);
        addOperand(lhs);
        addOperand(rhs);
    }

    protected IceBinaryInstruction(IceBlock parent, String name, IceType type, IceValue lhs, IceValue rhs) {
        super(parent, name, type);
        addOperand(lhs);
        addOperand(rhs);
    }

    public void addOperand(IceValue lhs, IceValue rhs) {
        this.addOperand(lhs);
        this.addOperand(rhs);
    }

    public IceValue getLhs() {
        return getOperand(0);
    }

    public IceValue getRhs() {
        return getOperand(1);
    }

    private void setLhs(IceValue lhs) {
        this.addOperand(lhs);
    }

    private void setRhs(IceValue rhs) {
        this.addOperand(rhs);
    }

    public void addNSW() {
        this.isNSW = true;
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = ");
        String opName = switch (this) {
            case Add _ -> "add";
            case FAdd _ -> "fadd";
            case Sub _ -> "sub";
            case FSub _ -> "fsub";
            case Mul _ -> "mul";
            case FMul _ -> "fmul";
            case Div _ -> "div";
            case SDiv _ -> "sdiv";
            case FDiv _ -> "fdiv";
            case Mod _ -> "srem";
            case Shl _ -> "shl";
            case Shr _ -> "shr";
            case And _ -> "and";
            case Or _ -> "or";
            case Xor _ -> "xor";
            default -> throw new IllegalStateException("Unexpected binary instruction: " + this.getClass().getSimpleName());
        };
        builder.append(opName);
        if (isNSW) {
            builder.append(" nsw");
        }
        builder.append(" ").append(getType()).append(" ")
                .append(getLhs().getReferenceName(false)).append(", ")
                .append(getRhs().getReferenceName(false));
    }

    public boolean isNSW() {
        return isNSW;
    }

    public int getTypeHash() {
        int hash = switch (this) {
            case Add _ -> 0x31451;
            case FAdd _ -> 0xfa195;
            case Sub _ -> 0x1ff39;
            case FSub _ -> 0x6b378;
            case Mul _ -> 0x0542b;
            case FMul _ -> 0xbb945;
            case Div _ -> 0xfad90;
            case SDiv _ -> 0x84add;
            case FDiv _ -> 0xcc603;
            case Mod _ -> 0x594e2;
            case Shl _ -> 0x1e2f3;
            case Shr _ -> 0xff304;
            case And _ -> 0x95510;
            case Or _ -> 0xabfdc;
            case Xor _ -> 0x105fc;
            default -> throw new IllegalStateException("Unexpected binary instruction: " + this.getClass().getSimpleName());
        };

        return hash;
    }
}
