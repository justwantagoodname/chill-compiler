package top.voidc.optimizer.pass.unit;

import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceCallInstruction;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.ds.ChilletGraph;
import top.voidc.optimizer.pass.CompilePass;

/**
 * 构造函数调用图，为有向图，节点为函数，边为函数调用关系。
 * <br>
 * 一条由函数A指向函数B的边表示函数A调用了函数B。
 * <br>
 * 当存在未使用的函数时，调用图不保证为连通图，<b>调用图仅仅反映了函数内的静态调用关系</b>
 */
@Pass(group = {"O0", "analysis"})
public class CallGraphAnalyzer implements CompilePass<IceUnit> {
    private final ChilletGraph<IceFunction> callGraph = new ChilletGraph<>();

    public CallGraphAnalyzer(IceContext context) {
        context.addPassResult("callGraph", callGraph);
    }

    public void analyzeCalls(IceFunction function) {
        for (var block : function) {
            for (var instruction : block) {
                if (instruction instanceof IceCallInstruction call) {
                    var target = call.getTarget();
                    assert target != null;
                    callGraph.addEdge(function, target);
                }
            }
        }
    }

    @Override
    public boolean run(IceUnit target) {
        callGraph.createNewNodes(target.getFunctions());
        for (IceFunction function : target.getFunctions()) {
            analyzeCalls(function);
        }
        Log.d("\n" + callGraph.getGraphEditorString());
        return false;
    }
}
