package top.voidc.backend.instr;

import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.backend.arm64.instr.pattern.ARM64InstructionPatternPack;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.Collection;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 对函数中的每一个基本块进行指令选择
 * 然后替换掉原有指令
 */
@Pass(group = {"O0", "needfix"})
public class InstructionSelectionPass implements CompilePass<IceFunction> {
    private final IceContext context;

    // TODO: 改为动态注入
    private final Collection<InstructionPattern> patternPack = new ARM64InstructionPatternPack().getPatternPack();

    public InstructionSelectionPass(IceContext context) {
        this.context = context;
    }

    @Override
    public boolean run(IceFunction target) {
        var machineFunction = new ARM64Function(target.getName());

        var blocks = target.blocks();
        for (var block : blocks) {
            var selector = new InstructionSelector(target, machineFunction, block, this.patternPack);
            if (!selector.doSelection()) {
                throw new RuntimeException("Error in selection");
            }
            Log.d("Block: " + block.getName() + " selected instructions: \n" + selector.getResult().stream().map(Objects::toString).collect(Collectors.joining("\n")));
        }
        return false;
    }
}
