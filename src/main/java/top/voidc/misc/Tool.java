package top.voidc.misc;

import top.voidc.backend.arm64.instr.ARM64Register;
import top.voidc.ir.ice.instruction.IceCmpInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceVecType;
import top.voidc.ir.machine.IceMachineRegister;

import java.util.List;

public class Tool {
    public static void TODO(String reason) {
        throw new UnsupportedOperationException("Not implemented yet: " + reason);
    }

    public static boolean inRange(int val, int min, int max) {
        return val >= min && val <= max;
    }

    /**
     * 计算整数的二进制对数
     * @author GitHub Copilot
     * @param value 要计算的值，必须为正整数
     * @return 二进制对数
     */
    public static int log2(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be positive");
        }
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    /**
     * 检查一个整数是否是2的幂
     * @param value 要检查的值，必须为正整数
     * @return 如果是2的幂返回true，否则返回false
     */
    public static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }


    /**
     * 检查一个整数是否可以12位立即数的形式表示
     * @param value 要检查的值
     * @return 如果可以按12位立即数返回true，否则返回false
     */
    public static boolean isImm12(long value) {
        return (value & 0xFFF) == value;
    }


    public static boolean isImm16(long value) {
        // 检查是否是16位立即数
        return (value & 0xFFFF) == value;
    }

    public static boolean isArm64FloatImmediate(float value) {
        return Arm64FloatImmediateSet.canBeArm64Immediate(value);
    }

    /**
     * 映射IR条件运算符到ARM64条件码
     */
    public static String mapToArm64Condition(IceCmpInstruction cond) {
        if (cond instanceof IceCmpInstruction.Icmp cmp) {
            return switch (cmp.getCmpType()) {
                case EQ -> "EQ";
                case NE -> "NE";
                case SLT -> "LT";
                case SLE -> "LE";
                case SGT -> "GT";
                case SGE -> "GE";
            };
        } else if(cond instanceof IceCmpInstruction.Fcmp cmp) {
            return switch (cmp.getCmpType()) {
                case OEQ -> "EQ";
                case ONE -> "NE";
                case OLT -> "LT";
                case OLE -> "LE";
                case OGT -> "GT";
                case OGE -> "GE";
            };
        }
        throw new IllegalArgumentException("Unsupported condition type");
    }

    public static String toGNUASCIIFormat(List<Byte> byteList) {
        var sb = new StringBuilder();
        for (Byte b : byteList) {
            sb.append(String.format("\\%03o", b));
        }
        return sb.toString();
    }

    public enum RegisterType {
        CALLER_SAVED, // 调用者保存寄存器
        CALLEE_SAVED,  // 被调用者保存寄存器
        READ_ONLY,         // 零寄存器
    }

    public static RegisterType getArm64RegisterType(IceMachineRegister reg) {
        assert reg instanceof ARM64Register;
        assert reg.getType().equals(IceType.I64) || reg.getType().equals(IceVecType.VEC128);

        if (reg.getType().equals(IceType.I64)) {
            // ARM64 的整数寄存器
            String name = reg.getName();
            if (name.equals("zr")) return RegisterType.READ_ONLY; // 零寄存器
            if (name.equals("sp") || name.equals("fp") || name.equals("lr")) {
                return RegisterType.CALLEE_SAVED; // sp, fp, lr 是被调用者保存寄存器
            }

            int id = Integer.parseInt(name);
            if (Tool.inRange(id, 0, 18)) { // x0 - x18
                return RegisterType.CALLER_SAVED; // Caller-saved registers
            } else if (Tool.inRange(id, 19, 30)) { // x19 - x28 x29(fp) x30(lr)
                return RegisterType.CALLEE_SAVED;
            } else {
                throw new IllegalArgumentException("Unknown register: " + name);
            }
        } else if (reg.getType().equals(IceVecType.VEC128)) {
            var name = reg.getName();
            var id = Integer.parseInt(name);
            if (Tool.inRange(id, 0, 7) || Tool.inRange(id, 16, 31)) { // v0 - v7 v16 - v31
                return RegisterType.CALLER_SAVED; // Caller-saved vector registers
            } else if (Tool.inRange(id, 8, 15)) { // v8 - v15
                // Note: 理论上只有低 64 位是 callee-saved 的
                // 但为了简化处理，这里将 v8 - v15 也视为 callee-saved
                // 这意味着在使用这些寄存器时需要保存全部 128 位没有什么影响
                return RegisterType.CALLEE_SAVED; // Callee-saved vector registers
            } else {
                throw new IllegalArgumentException("Unknown vector register: " + name);
            }
        }
        throw new IllegalStateException("Unknown register type: " + reg.getType());
    }
}
