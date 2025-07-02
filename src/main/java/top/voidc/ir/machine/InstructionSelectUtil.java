package top.voidc.ir.machine;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;

import java.util.function.BiPredicate;

public class InstructionSelectUtil {
    public static boolean isReg(IceValue value) {
        return !(value instanceof IceConstantData);
    }

    public static boolean commutativePredicate(IceBinaryInstruction instr, BiPredicate<IceValue, IceValue> biPredicate) {
        return biPredicate.test(instr.getLhs(), instr.getRhs()) || biPredicate.test(instr.getRhs(), instr.getLhs());
    }
}
