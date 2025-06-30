package top.voidc.optimizer.pass.function;

import top.voidc.ir.ice.constant.IceFunction;

import org.junit.jupiter.api.Test;
import top.voidc.misc.Log;

public class GlobalValueNumberingTest {
    @Test
    public void simpleGVN() {
        IceFunction function = IceFunction.fromTextIR("""
                define void @testFunction() {
                    %entry:
                        %0 = add i32 1, 2
                        %1 = add i32 1, 2
                        %p = add i32 2, 1
                        %2 = add i32 5, 6
                        %3 = add i32 1, 2
                        %4 = add i32 5, 6
                        %q = add i32 100, 300
                        %lmq = add i32 6, 5
                        %ouc = add i32 300, 100
                        %important = add i32 %1, %3
                        %other = add i32 %lmq, %ouc
                        ret void
                }
                """);

        GlobalValueNumbering gvn = new GlobalValueNumbering();
        gvn.run(function);
        Log.d(function.getTextIR());
    }
}
