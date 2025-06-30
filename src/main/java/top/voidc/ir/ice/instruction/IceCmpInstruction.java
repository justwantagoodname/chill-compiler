package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

public abstract class IceCmpInstruction extends IceInstruction {
    public static class Icmp extends IceCmpInstruction {
        public enum Type {
            EQ, NE, SLT, SLE, SGT, SGE;
            
            @Override 
            public String toString() {
                return name().toLowerCase();
            }
            
            public static Type fromSysyLiteral(String literal) {
                return switch (literal) {
                    case "==" -> EQ;
                    case "!=" -> NE;
                    case "<" -> SLT;
                    case "<=" -> SLE;
                    case ">" -> SGT;
                    case ">=" -> SGE;
                    default -> throw new IllegalArgumentException("Unknown comparison operator: " + literal);
                };
            }
        }

        private final Type cmpType;

        public Type getCmpType() {
            return cmpType;
        }

        public Icmp(IceBlock parent, String name, Type cmpType, IceValue lhs, IceValue rhs) {
            super(parent, name, lhs, rhs);
            Log.should(lhs.getType().isInteger(), "icmp operands must be integer");
            this.cmpType = cmpType;
        }

        public Icmp(IceBlock parent, Type cmpType, IceValue lhs, IceValue rhs) {
            super(parent, lhs, rhs);
            Log.should(lhs.getType().isInteger(), "icmp operands must be integer");
            this.cmpType = cmpType;
        }

        @Override
        public void getTextIR(StringBuilder builder) {
            builder.append("%").append(getName()).append(" = icmp ").append(cmpType).append(" ")
                    .append(getOperand(0).getReferenceName(true)).append(", ")
                    .append(getOperand(1).getReferenceName(false));
        }
    }

    public static class Fcmp extends IceCmpInstruction {
        public enum Type {
            OEQ, ONE, OLT, OLE, OGT, OGE;
            
            @Override 
            public String toString() {
                return name().toLowerCase();
            }
            
            public static Type fromSysyLiteral(String literal) {
                return switch (literal) {
                    case "==" -> OEQ;
                    case "!=" -> ONE;
                    case "<" -> OLT;
                    case "<=" -> OLE;
                    case ">" -> OGT;
                    case ">=" -> OGE;
                    default -> throw new IllegalArgumentException("Unknown comparison operator: " + literal);
                };
            }
        }

        private final Type cmpType;

        public Type getCmpType() {
            return cmpType;
        }

        public Fcmp(IceBlock parent, String name, Type cmpType, IceValue lhs, IceValue rhs) {
            super(parent, name, lhs, rhs);
            Log.should(lhs.getType().isFloat(), "fcmp operands must be float");
            this.cmpType = cmpType;
        }

        public Fcmp(IceBlock parent, Type cmpType, IceValue lhs, IceValue rhs) {
            super(parent, lhs, rhs);
            Log.should(lhs.getType().isFloat(), "fcmp operands must be float");
            this.cmpType = cmpType;
        }

        @Override
        public void getTextIR(StringBuilder builder) {
            builder.append("%").append(getName()).append(" = fcmp ").append(cmpType).append(" ")
                    .append(getOperand(0).getReferenceName(true)).append(", ")
                    .append(getOperand(1).getReferenceName(false));
        }
    }

    protected IceCmpInstruction(IceBlock parent, String name, IceValue lhs, IceValue rhs) {
        super(parent, name, IceType.I1);
        Log.should(lhs.getType().equals(rhs.getType()), "cmp operands must have same type");
        addOperand(lhs);
        addOperand(rhs);
    }

    protected IceCmpInstruction(IceBlock parent, IceValue lhs, IceValue rhs) {
        super(parent, IceType.I1);
        Log.should(lhs.getType().equals(rhs.getType()), "cmp operands must have same type");
        addOperand(lhs);
        addOperand(rhs);
    }

    public IceValue getLhs() {
        return getOperand(0);
    }

    public IceValue getRhs() {
        return getOperand(1);
    }
}
