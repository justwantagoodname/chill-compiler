package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.backend.instr.InstructionPack;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.machine.IceMachineFunction;

import java.util.ArrayList;
import java.util.List;

public class ARM64InstructionPatternPack implements InstructionPack {
    private static final List<InstructionPattern<?>> patternSet = new ArrayList<>();

    static {
        patternSet.add(new ArithmaticInstructionPattern.ADDTwoReg());
        patternSet.add(new ArithmaticInstructionPattern.ADDImm());
        patternSet.add(new ArithmaticInstructionPattern.MULTwoReg());
//        patternSet.add(new ArithmaticInstructionPattern.MULImm());
        patternSet.add(new ArithmaticInstructionPattern.MADDInstruction());
        patternSet.add(new ArithmaticInstructionPattern.MSUBInstruction());
        patternSet.add(new ArithmaticInstructionPattern.SUBTwoReg());
        patternSet.add(new ArithmaticInstructionPattern.SUBImm());
        patternSet.add(new ArithmaticInstructionPattern.SDIVTwoReg());
        patternSet.add(new ArithmaticInstructionPattern.NEGReg());
        patternSet.add(new ArithmaticInstructionPattern.SMODTwoReg());
        patternSet.add(new ArithmaticInstructionPattern.ZextBoolToInt());
        patternSet.add(new ArithmaticInstructionPattern.ZextBoolImmToInt());

        patternSet.add(new ControlInstructionPattern.BranchUnconditional());
        patternSet.add(new ControlInstructionPattern.RetVoid());
        patternSet.add(new ControlInstructionPattern.RetInteger());
        patternSet.add(new ControlInstructionPattern.VoidCall());
        patternSet.add(new ControlInstructionPattern.IntCall());

        patternSet.add(new LoadAndStorePattern.LoadRegFuncParam());
        patternSet.add(new LoadAndStorePattern.LoadIntImmediateToReg());
        patternSet.add(new LoadAndStorePattern.LoadIntZeroToReg());
        patternSet.add(new LoadAndStorePattern.CopyImm());
        patternSet.add(new LoadAndStorePattern.CopyInst());
        patternSet.add(new LoadAndStorePattern.DummyPHI());

        patternSet.add(new BitwisestructionPattern.LSLInstruction());
        patternSet.add(new BitwisestructionPattern.ASRInstruction());
        patternSet.add(new BitwisestructionPattern.ANDInstruction());
        patternSet.add(new BitwisestructionPattern.ORRInstruction());
        patternSet.add(new BitwisestructionPattern.EORInstruction());

        patternSet.add(new ConditionPatterns.CMPReg());
        patternSet.add(new ConditionPatterns.CMPImm());
        patternSet.add(new ConditionPatterns.CondBranch());

        patternSet.add(new MemoryAllocationPattern.SimpleAlloca());
//        patternSet.add(new MemoryAllocationPattern.SimpleAllocaPointer());
        patternSet.add(new MemoryAllocationPattern.ArrayAlloca());
//        patternSet.add(new MemoryAllocationPattern.ArrayAllocaPointer());
        patternSet.add(new MemoryAllocationPattern.MemsetIntrinsic());
        patternSet.add(new MemoryAllocationPattern.MemcpyIntrinsic());

        patternSet.add(new MemoryAccessPatterns.LoadStackPattern());
        patternSet.add(new MemoryAccessPatterns.StoreStackPattern());
        patternSet.add(new MemoryAccessPatterns.LoadRegisterPointerPattern());
        patternSet.add(new MemoryAccessPatterns.StoreRegisterPointerPattern());
        patternSet.add(new MemoryAccessPatterns.LoadGlobalPointer());
        patternSet.add(new MemoryAccessPatterns.GEPLoadGlobalPointer());
        patternSet.add(new MemoryAccessPatterns.GEPLoadLocalPointer());
        patternSet.add(new MemoryAccessPatterns.GEPLoadArgumentPointer());
    }

    @Override
    public List<InstructionPattern<?>> getPatternPack() {
        return patternSet;
    }

    @Override
    public IceMachineFunction createMachineFunction(IceFunction iceFunction) {
        return new ARM64Function(iceFunction);
    }
}
