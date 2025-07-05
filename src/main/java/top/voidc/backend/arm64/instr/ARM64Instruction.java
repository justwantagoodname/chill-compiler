package top.voidc.backend.arm64.instr;

import top.voidc.ir.IceValue;
import top.voidc.ir.machine.IceMachineInstruction;

import java.util.Set;

public class ARM64Instruction extends IceMachineInstruction {
    public ARM64Instruction(String renderTemplate) {
        super(renderTemplate);
    }

    public ARM64Instruction(String renderTemplate, IceValue... values) {
        super(renderTemplate, values);
    }

    @Override
    public String getArchitecture() {
        return "aarch64";
    }

    @Override
    public String getABIName() {
        return "linux-gnu-glibc";
    }

    @Override
    public int getBitSize() {
        return 64;
    }

    @Override
    public boolean isTerminal() {
        return Set.of("RET", "B").contains(getOpcode());
    }
}
