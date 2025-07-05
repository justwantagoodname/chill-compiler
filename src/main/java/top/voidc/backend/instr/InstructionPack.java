package top.voidc.backend.instr;

import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.machine.IceMachineFunction;

import java.util.Collection;

public interface InstructionPack {
    Collection<InstructionPattern<?>> getPatternPack();

    IceMachineFunction createMachineFunction(IceFunction iceFunction);
}
