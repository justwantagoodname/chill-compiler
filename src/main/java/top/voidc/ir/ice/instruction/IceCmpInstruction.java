package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

public class IceCmpInstruction extends IceInstruction {

    public enum CmpType {
        EQ,
        NE,
        SLT,
        SLE,
        SGT,
        SGE,

        OEQ,
        ONE,
        OLT,
        OLE,
        OGT,
        OGE;

        @Override
        public String toString() {
            return switch (this) {
                case EQ -> "eq";
                case NE -> "ne";
                case SLT -> "slt";
                case SLE -> "sle";
                case SGT -> "sgt";
                case SGE -> "sge";
                case OEQ -> "oeq";
                case ONE -> "one";
                case OLT -> "olt";
                case OLE -> "ole";
                case OGT -> "ogt";
                case OGE -> "oge";
            };
        }

        public static CmpType fromSysyLiteral(String literal, boolean isFloat) {
            return switch (literal) {
                case "==" -> isFloat ? OEQ : EQ;
                case "!=" -> isFloat ? ONE : NE;
                case "<" -> isFloat ? OLT : SLT;
                case "<=" -> isFloat ? OLE : SLE;
                case ">" -> isFloat ? OGT : SGT;
                case ">=" -> isFloat ? OGE : SGE;
                default -> throw new IllegalArgumentException("Unknown comparison operator: " + literal);
            };
        }
    }

    protected final CmpType cmpType;

    public IceCmpInstruction(IceBlock parent, String name, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, name, IceType.I1);
        setInstructionType(InstructionType.CMP);
        Log.should(lhs.getType().equals(rhs.getType()), "cmp 指令操作数类型不匹配");

        this.cmpType = cmpType;
        this.addOperand(lhs);
        this.addOperand(rhs);
    }

    public IceCmpInstruction(IceBlock parent, CmpType cmpType, IceValue lhs, IceValue rhs) {
        super(parent, IceType.I1);
        setInstructionType(InstructionType.CMP);
        Log.should(lhs.getType().equals(rhs.getType()), "cmp 指令操作数类型不匹配");

        this.cmpType = cmpType;
        this.addOperand(lhs);
        this.addOperand(rhs);
    }

    public CmpType getCmpType() {
        return cmpType;
    }

    public IceValue getLhs() {
        return getOperand(0);
    }

    public IceValue getRhs() {
        return getOperand(1);
    }
}
