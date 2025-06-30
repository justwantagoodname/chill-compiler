package top.voidc.backend.instr;

import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.optimizer.pass.CompilePass;

import java.util.Set;

public class InstructionSelector {
    private final IceContext context;
    private final Set<InstructionPattern>  patternSet;

    public InstructionSelector(IceContext context, Set<InstructionPattern> patternSet) {
        this.context = context;
        this.patternSet = patternSet;
    }

    /**
     * 将IceUnit中的每个函数进行执行选择
     * @return 是否匹配成功
     */
    boolean match() {
        return false;
    }
}
