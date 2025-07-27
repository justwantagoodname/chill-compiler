package top.voidc.backend.regallocator;

import top.voidc.backend.LivenessAnalysis;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class LinearScanAllocator implements CompilePass<IceMachineFunction> {

    private final LivenessAnalysis.LivenessResult livenessResult;
    private final IceContext iceContext;
    private List<IceBlock> BBs;
    Map<IceBlock, LivenessAnalysis.BlockLivenessData> functionLivenessData;

    private static class LiveInterval {
        IceMachineRegister vreg, preg;
        int start, end;

        public LiveInterval(IceMachineRegister vreg, int start, int end) {
            this.vreg = vreg;
            this.start = start;
            this.end = end;
        }
    }


    public LinearScanAllocator(IceContext context, LivenessAnalysis.LivenessResult livenessResult) {
        this.livenessResult = livenessResult;
        this.iceContext = context;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        functionLivenessData = livenessResult.getLivenessData(target);

        this.BBs = target.getBlocks();

        return false;
    }

    private List<LiveInterval> buildLiveIntervals(IceMachineInstruction mf) {
        Map<IceMachineRegister, LiveInterval> intervalMap = new HashMap<>();

        int currentIndex = 0;
        for (var block : BBs) {
            for (var instruction : block) {
                if (!(instruction instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function?");
                }
                for (IceValue operand : inst.getSourceOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView rv) {
                        IceMachineRegister reg = rv.getRegister();
                        if (reg.isVirtualize()) {
                            intervalMap.putIfAbsent(reg, new LiveInterval(reg, currentIndex, currentIndex));
                        }
                    }
                }

                IceMachineRegister.RegisterView rv = inst.getResultReg();
                if (rv != null && rv.getRegister().isVirtualize()) {
                    IceMachineRegister reg = rv.getRegister();
                    intervalMap.putIfAbsent(reg, new LiveInterval(reg, currentIndex, currentIndex));
                }

                ++currentIndex;
            }
        }

        for (var block : BBs) {
            var liveOut = functionLivenessData.get(block).liveOut();
            for (IceValue value : liveOut) {
                if (value instanceof IceMachineRegister.RegisterView rv) {
                    IceMachineRegister reg = rv.getRegister();
                    if (reg.isVirtualize()) {
                        LiveInterval interval = intervalMap.get(reg);
                        if (interval != null) {
                            interval.end = currentIndex; // 更新结束位置
                        }
                    }
                }
            }
        }

        return null;
    }
}
