package top.voidc.optimizer.pass.function;

import org.junit.jupiter.api.Test;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceFunction;

public class LivenessAnalysisTest {
    @Test
    public void testAnalysis() {
        final var target = IceFunction.fromTextIR("""
                define void @testFunction() {
                    entry:
                        %0 = add i32 1, 2
                        %1 = add i32 1, %0
                }
                """);
    }
}
