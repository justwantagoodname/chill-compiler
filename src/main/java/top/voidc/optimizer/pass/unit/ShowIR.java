package top.voidc.optimizer.pass.unit;

import top.voidc.ir.IceUnit;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

// Debug 用途就是打印 IR
@Pass(enable = true)
public class ShowIR implements CompilePass<IceUnit> {
    @Override
    public String getName() {
        return "ShowIR";
    }

    @Override
    public boolean run(IceUnit target) {
        if (System.getProperty("chill.ci") != null && System.getProperty("chill.ci").equals("true")) {
            // CI 环境下不打印 IR
            return false;
        }
        Log.d("\n" + target.getTextIR());
        return false;
    }
}
