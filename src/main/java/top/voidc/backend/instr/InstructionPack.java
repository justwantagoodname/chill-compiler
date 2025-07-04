package top.voidc.backend.instr;

import java.util.Collection;

public interface InstructionPack {
    Collection<InstructionPattern<?>> getPatternPack();
}
