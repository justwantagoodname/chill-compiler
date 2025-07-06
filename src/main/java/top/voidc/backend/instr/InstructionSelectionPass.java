package top.voidc.backend.instr;

import top.voidc.backend.arm64.instr.pattern.ARM64InstructionPatternPack;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.Collection;
import java.util.List;

/**
 * 对函数中的每一个基本块进行指令选择
 * 然后替换掉原有指令
 */
@Pass(group = {"O0"})
public class InstructionSelectionPass implements CompilePass<IceUnit> {
    private final InstructionPack instructionPack;
    private final Collection<InstructionPattern<?>> patternPack;

    public InstructionSelectionPass(InstructionPack instructionPack) {
        this.instructionPack = instructionPack;
        this.patternPack = instructionPack.getPatternPack();
    }

    @Override
    public boolean run(IceUnit unit) {
        for (var target : List.copyOf(unit.getFunctions())) {
            if (target instanceof IceExternFunction) continue; // 跳过外部函数

            var machineFunction = instructionPack.createMachineFunction(target);
            var blocks = target.blocks();

            for (var block : blocks) {
                var selector = new InstructionSelector(target, machineFunction, block, this.patternPack);
                if (!selector.doSelection()) {
                    throw new RuntimeException("Error in selection");
                }
                var selectedInstructions = selector.getResult();
                var machineBlock = machineFunction.getMachineBlock(block.getName());
                selectedInstructions.forEach(instruction -> instruction.setParent(machineBlock));
                selectedInstructions.forEach(machineBlock::addInstruction);
//            Log.d("Block: " + block.getName() + " selected instructions: \n" + selector.getResult().stream().map(Objects::toString).collect(Collectors.joining("\n")));
            }

            target.replaceAllUsesWith(machineFunction);
            assert target.getUsers().isEmpty();

            unit.removeFunction(target);
            unit.addFunction(machineFunction);
        }
        return true;
    }
}
