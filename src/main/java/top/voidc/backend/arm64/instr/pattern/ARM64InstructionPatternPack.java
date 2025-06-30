package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.instr.InstructionPack;
import top.voidc.backend.instr.InstructionPattern;

import java.util.ArrayList;
import java.util.List;

public class ARM64InstructionPatternPack implements InstructionPack {
    private static final List<InstructionPattern> patternSet = new ArrayList<>();

    static {
        patternSet.add(new ArithmaticInstructionPattern.ADDTwoReg());
        patternSet.add(new ArithmaticInstructionPattern.MULTwoReg());
        patternSet.add(new ArithmaticInstructionPattern.MADDInstruction());
        patternSet.add(new ControlInstructionPattern.RetVoid());
        patternSet.add(new MemoryInstructionPattern.LoadRegFuncParam());
        patternSet.add(new ControlInstructionPattern.RetInteger());
    }

    @Override
    public List<InstructionPattern> getPatternPack() {
        return patternSet;
    }
}
