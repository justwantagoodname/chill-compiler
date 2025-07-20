package top.voidc.backend.arm64.instr;

import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.machine.IceMachineInstruction;

import java.util.Set;

public class ARM64Instruction extends IceMachineInstruction {
    public ARM64Instruction(String renderTemplate) {
        super(renderTemplate);
    }

    public ARM64Instruction(String renderTemplate, IceMachineValue... values) {
        super(renderTemplate, values);
    }

    @Override
    public String getArchitecture() {
        return "armv8-a";
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
        if (getOpcode().startsWith("B.")) return true;
        return Set.of("RET", "B").contains(getOpcode());
    }

    @Override
    public IceMachineInstruction clone() {
        var clone = new ARM64Instruction(this.renderTemplate);
        clone.setName(getName());
        for (var operand : getOperands()) {
            clone.addOperand(operand);
        }
        return clone;
    }
}
