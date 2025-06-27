package top.voidc.optimizer.pass.function;

import org.junit.jupiter.api.Test;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.Log;

public class LivenessAnalysisTest {
    @Test
    public void testAnalysis() {
        final var target = IceFunction.fromTextIR("""
                define void @testFunction() {
                    %entry:
                        %0 = add i32 1, 2
                        br label %B1
                    %B1:
                        %1 = add i32 2, 3
                        br label %B2
                    %B2:
                        %2 = add i32 3, %0
                        ret void
                }
                """);
        Log.d(target.getTextIR());

    }
}
