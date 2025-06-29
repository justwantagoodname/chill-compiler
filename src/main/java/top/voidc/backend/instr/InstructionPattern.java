package top.voidc.backend.instr;

import top.voidc.ir.IceValue;
import java.util.function.Predicate;

public interface InstructionPattern extends Predicate<IceValue> {
    default int getCost() {
        return 0;
    }
}
