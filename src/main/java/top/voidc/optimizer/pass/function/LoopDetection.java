package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Pass(group = {"O0", "analysis"})
public class LoopDetection implements CompilePass<IceFunction> {

    private static class LoopInfo {
        final IceBlock header;
        final Set<IceBlock> blocks = new HashSet<>();
        final List<IceBlock> latches = new ArrayList<>();

        LoopInfo(IceBlock header) {
            this.header = header;
            blocks.add(header);
        }
    }

    public LoopDetection(IceContext context) {

    }

    @Override
    public boolean run(IceFunction target) {
        return false;
    }
}
