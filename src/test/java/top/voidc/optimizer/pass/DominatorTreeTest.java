package top.voidc.optimizer.pass;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.Log;

class DominatorTreeTest {
    private IceFunction function;
    private IceBlock[] blocks;
    private DominatorTree dominatorTree;
    private IceBlock[] expectedDominators;

    @Test
    void testDominatorRelations() {
        function = new IceFunction("testFunction");
        blocks = new IceBlock[]{
                function.getEntryBlock(),
                new IceBlock(function, "b2"),
                new IceBlock(function, "b3"),
                new IceBlock(function, "b4"),
                new IceBlock(function, "b5"),
                new IceBlock(function, "b6")
        };

        // 构建CFG（控制流图）
        blocks[0].addSuccessor(blocks[1]);     // entry -> b2
        blocks[1].addSuccessor(blocks[2]);     // b2 -> b3
        blocks[1].addSuccessor(blocks[3]);     // b2 -> b4
        blocks[2].addSuccessor(blocks[4]);     // b3 -> b5
        blocks[3].addSuccessor(blocks[4]);     // b4 -> b5
        blocks[4].addSuccessor(blocks[5]);     // b5 -> b6

        expectedDominators = new IceBlock[]{null, blocks[0], blocks[1], blocks[1], blocks[1], blocks[4]};
        dominatorTree = new DominatorTree(function);

        for (int i = 0; i < blocks.length; i++) {
            IceBlock actualDominator = dominatorTree.getDominator(blocks[i]);
            IceBlock expectedDominator = expectedDominators[i];
            
            if (expectedDominator == null) {
                // 对于入口块，其支配节点为null
                assertNull(actualDominator, String.format("入口块 %s 的支配节点应该为null", blocks[i].getName()));
            } else {
                assertEquals(expectedDominator.getName(), actualDominator.getName(),
                    String.format("块 %s 的支配节点应该为 %s，但得到了 %s", 
                        blocks[i].getName(), 
                        expectedDominator.getName(), 
                        actualDominator.getName()));
            }
        }
    }

    @Test
    void testDominatorRelations2() {
        function = new IceFunction("testFunction");
        blocks = new IceBlock[]{
                function.getEntryBlock(),
                new IceBlock(function, "b1"),
                new IceBlock(function, "b2"),
                new IceBlock(function, "b3"),
                new IceBlock(function, "b4"),
                new IceBlock(function, "b5")
        };

        // 构建CFG（控制流图）
        blocks[0].addSuccessor(blocks[1]);
        blocks[0].addSuccessor(blocks[2]);
        blocks[1].addSuccessor(blocks[3]);
        blocks[1].addSuccessor(blocks[4]);
        blocks[2].addSuccessor(blocks[5]);
        blocks[3].addSuccessor(blocks[4]);
        blocks[4].addSuccessor(blocks[5]);

        expectedDominators = new IceBlock[]{null, blocks[0], blocks[0], blocks[1], blocks[1], blocks[0]};
        dominatorTree = new DominatorTree(function);

        for (int i = 0; i < blocks.length; i++) {
            IceBlock actualDominator = dominatorTree.getDominator(blocks[i]);
            IceBlock expectedDominator = expectedDominators[i];
            Log.d(dominatorTree.getDominatees(blocks[i]).toString());
            if (expectedDominator == null) {
                // 对于入口块，其支配节点为null
                assertNull(actualDominator, String.format("入口块 %s 的支配节点应该为null", blocks[i].getName()));
            } else {
                assertEquals(expectedDominator.getName(), actualDominator.getName(),
                        String.format("块 %s 的支配节点应该为 %s，但得到了 %s",
                                blocks[i].getName(),
                                expectedDominator.getName(),
                                actualDominator.getName()));
            }
        }
    }

}
