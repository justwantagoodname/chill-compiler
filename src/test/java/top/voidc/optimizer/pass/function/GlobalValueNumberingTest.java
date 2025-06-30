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
                        %2 = add i32 5, 6
                        %3 = add i32 1, 2
                        %4 = add i32 5, 6
                        ret void
                }
                """);

        GlobalValueNumbering gvn = new GlobalValueNumbering();
        gvn.run(function);
        Log.d(function.getTextIR());
    }
}
