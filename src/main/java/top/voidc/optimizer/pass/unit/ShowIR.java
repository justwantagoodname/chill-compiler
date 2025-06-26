package top.voidc.optimizer.pass.unit;

import top.voidc.ir.IceUnit;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

// Debug 用途就是打印 IR
@Pass
public class ShowIR implements CompilePass<IceUnit> {
    @Override
    public String getName() {
        return "ShowIR";
    }

    @Override
    public boolean run(IceUnit target) {
        Log.d(target.getTextIR());
        return false;
    }
}
