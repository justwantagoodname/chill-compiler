package top.voidc.backend;

import org.junit.jupiter.api.Test;
import top.voidc.backend.arm64.instr.pattern.ARM64InstructionPatternPack;
import top.voidc.backend.instr.InstructionSelectionPass;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.Log;
import top.voidc.optimizer.PassManager;
import top.voidc.optimizer.pass.unit.ShowIR;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

public class LivenessAnalysisTest {

    public static IceContext runOnFunction(String textIR) {
        IceFunction target = IceFunction.fromTextIR(textIR);
        IceContext context = new IceContext();

        IceUnit unit = new IceUnit("testUnit");
        unit.addFunction(target);
        context.addPassResult(new ARM64InstructionPatternPack());

        context.setCurrentIR(unit);
        PassManager passManager = new PassManager(context);
        passManager.setPipeline(pm -> {
            pm.runPass(SSADestruction.class);
            pm.runPass(InstructionSelectionPass.class);
            pm.runPass(ShowIR.class);
            pm.runPass(LivenessAnalysis.class);
        });

        passManager.runAll();
        return context;
    }

    @Test
    public void testAnalysis() {
        var context = runOnFunction("""
                define i32 @foo() {
                entry:
                	br label %while.cond
                while.cond:
                	%0 = phi i32 [ 0, %entry ], [ %1, %while.body ]
                	br label %while.body
                while.body:
                	%1 = add i32 %0, 1
                	br label %while.cond
                }
                """);

        assertTrue(context.getPassResults().stream().anyMatch(result -> result instanceof LivenessAnalysis.LivenessResult));
        var livenessResult = (LivenessAnalysis.LivenessResult)
            context.getPassResults().stream().filter(result -> result instanceof LivenessAnalysis.LivenessResult).findFirst().get();

        var livenessData = livenessResult.getLivenessData(context.getCurrentIR().getFunctions().getFirst());
        assertNotNull(livenessData);

        var liveInSize = Map.of(".L0", 0, ".L1", 1, ".L2", 1);
        var liveOutSize = Map.of(".L0", 1, ".L1", 1, ".L2", 1);
        for (var entry : livenessData.entrySet()) {
            var block = entry.getKey();
            var data = entry.getValue();
            assertEquals(liveInSize.get(block.getName()), data.liveIn().size(), "Live In size mismatch for block: " + block.getName());
            assertEquals(liveOutSize.get(block.getName()), data.liveOut().size(), "Live Out size mismatch for block: " + block.getName());
            Log.d("Block: " + block.getName() + ", Live In: " + data.liveIn() + ", Live Out: " + data.liveOut());
        }
    }
}
