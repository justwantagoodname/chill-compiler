package top.voidc.ir.machine;

import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;

import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.BiPredicate;

public class InstructionSelectUtil {
    public static boolean isImm16(IceValue value) {
        return value instanceof IceConstantInt intValue && ((int) intValue.getValue() >> 16) == 0;
    }

    public static boolean isImm12(IceValue value) {
        return value instanceof IceConstantInt intValue && ((int) intValue.getValue() >> 12) == 0;
    }

    public static boolean isConstInt(IceValue value) {
        return value instanceof IceConstantInt;
    }

    /**
     * 检查给定的值是否可以被指令选择器选择为能够产生寄存器结果的模式
     * 这是一个动态检查，考虑了指令选择器的当前状态和可用模式
     */
    public static boolean canBeReg(InstructionSelector selector, IceValue value) {
        // 如果是一个instruction那一定是一个寄存器
        if (value instanceof IceInstruction) return true;

        try {
            // 如果指令选择器能够为该值选择一个模式，则认为它可以是寄存器
            var matchResult = selector.select(value);
            return matchResult != null;
        } catch (Exception e) {
            // 如果选择失败，则不能作为寄存器使用
            return false;
        }
    }

    public static boolean commutativeTest(IceBinaryInstruction instr, BiPredicate<IceValue, IceValue> biPredicate) {
        return biPredicate.test(instr.getLhs(), instr.getRhs()) || biPredicate.test(instr.getRhs(), instr.getLhs());
    }

    @SuppressWarnings("unchecked")
    public static <A extends IceValue, B extends IceValue> void commutativeAccept(IceBinaryInstruction instr, BiPredicate<IceValue, IceValue> biPredicate, BiConsumer<A, B> biConsumer) {
        if (biPredicate.test(instr.getLhs(), instr.getRhs())) {
            biConsumer.accept((A) instr.getLhs(), (B) instr.getRhs());
        } else if (biPredicate.test(instr.getRhs(), instr.getLhs())) {
            biConsumer.accept((A) instr.getRhs(), (B) instr.getLhs());
        } else {
            throw new IllegalStateException();
        }
    }
    
    @SuppressWarnings("unchecked")
    public static <A extends IceValue, B extends IceValue, R> R commutativeApply(IceBinaryInstruction instr, BiPredicate<IceValue, IceValue> biPredicate, BiFunction<A, B, R> biFunction) {
        if (biPredicate.test(instr.getLhs(), instr.getRhs())) {
            return biFunction.apply((A) instr.getLhs(), (B) instr.getRhs());
        } else if (biPredicate.test(instr.getRhs(), instr.getLhs())) {
            return biFunction.apply((A) instr.getRhs(), (B) instr.getLhs());
        } else {
            throw new IllegalStateException();
        }
    }
}
