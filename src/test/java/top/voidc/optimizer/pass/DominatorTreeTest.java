package top.voidc.optimizer.pass;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.Log;

import java.util.Comparator;

/**
 * block 引用已经改了所以这个单元测试需要重写成按照指令引用block的形式
 */
class DominatorTreeTest {
    private IceFunction function;
    private DominatorTree<IceBlock> dominatorTree;
    private IceBlock[] expectedDominators;

    @Test
    void testDominatorRelations() {
        // 构建CFG（控制流图）
        // entry -> b2
        // b2 -> b3
        // b2 -> b4
        // b3 -> b5
        // b4 -> b5
        // b5 -> b6
        function = IceFunction.fromTextIR("""
                define void @f() {
                entry:
                	br label %b2
                b2:
                    br i1 true, label %b3, label %b4
                b3:
                    br label %b5
                b4:
                    br label %b5
                b5:
                    br label %b6
                b6:
                    ret void
                }
               """);
        var blocks = function.blocks();
        blocks.sort(Comparator.comparing(IceValue::getName));
        blocks.addFirst(blocks.removeLast());

        // [entry, b2, b3, b4, b5, b6]

        expectedDominators = new IceBlock[]{null, blocks.getFirst(), blocks.get(1), blocks.get(1), blocks.get(1), blocks.get(4)};
        var graph = function.getControlFlowGraph();
        var entryNodeId = graph.getNodeId(function.getEntryBlock());
        dominatorTree = new DominatorTree<>(graph, entryNodeId);

        for (int i = 0; i < blocks.size(); i++) {
            IceBlock actualDominator = dominatorTree.getDominator(blocks.get(i));
            IceBlock expectedDominator = expectedDominators[i];

            if (expectedDominator == null) {
                // 对于入口块，其支配节点为null
                assertNull(actualDominator, String.format("入口块 %s 的支配节点应该为null", blocks.get(i).getName()));
            } else {
                assertEquals(expectedDominator.getName(), actualDominator.getName(),
                    String.format("块 %s 的支配节点应该为 %s，但得到了 %s",
                        blocks.get(i).getName(),
                        expectedDominator.getName(),
                        actualDominator.getName()));
            }
        }
    }

    @Test
    void testDominatorRelations2() {
        // 构建CFG（控制流图）
        // entry -> b1, b2
        // b1 -> b3, b4
        // b2 -> b5
        // b3 -> b4
        // b4 -> b5
        function = IceFunction.fromTextIR("""
                define void @testFunction() {
                entry:
                    br i1 true, label %b1, label %b2
                b1:
                    br i1 true, label %b3, label %b4
                b2:
                    br label %b5
                b3:
                    br label %b4
                b4:
                    br label %b5
                b5:
                    ret void
                }
               """);
        var blocks = function.blocks();
        blocks.sort(Comparator.comparing(IceValue::getName));
        blocks.addFirst(blocks.removeLast());

        // [entry, b1, b2, b3, b4, b5]
        
        expectedDominators = new IceBlock[]{null, blocks.get(0), blocks.get(0), blocks.get(1), blocks.get(1), blocks.get(0)};
        var graph = function.getControlFlowGraph();
        var entryNodeId = graph.getNodeId(function.getEntryBlock());
        dominatorTree = new DominatorTree<>(graph, entryNodeId);

        for (int i = 0; i < blocks.size(); i++) {
            IceBlock actualDominator = dominatorTree.getDominator(blocks.get(i));
            IceBlock expectedDominator = expectedDominators[i];
            
            if (expectedDominator == null) {
                // 对于入口块，其支配节点为null
                assertNull(actualDominator, String.format("入口块 %s 的支配节点应该为null", blocks.get(i).getName()));
            } else {
                assertEquals(expectedDominator.getName(), actualDominator.getName(),
                    String.format("块 %s 的支配节点应该为 %s，但得到了 %s",
                        blocks.get(i).getName(),
                        expectedDominator.getName(),
                            actualDominator.getName()));
            }
        }
    }

}
