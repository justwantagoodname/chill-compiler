package top.voidc.backend;

import top.voidc.ir.IceContext;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

@Pass(group = {"O0", "backend"})
public class LinearScanAllocator implements CompilePass<IceMachineFunction> {

    private final LivenessAnalysis.LivenessResult livenessResult;
    private final IceContext iceContext;

    public LinearScanAllocator(IceContext context, LivenessAnalysis.LivenessResult livenessResult) {
        this.livenessResult = livenessResult;
        this.iceContext = context;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        var functionLivenessData = livenessResult.getLivenessData(target);
        
        return false;
    }
}
