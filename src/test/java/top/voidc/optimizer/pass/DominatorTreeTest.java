package top.voidc.optimizer.pass;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;

public class DominatorTreeTest {
    public static void main(String[] args) {
        IceFunction function = new IceFunction("testFunction");

        IceBlock[] b = {
            function.getEntryBlock(),
            new IceBlock(function, "b2"),
            new IceBlock(function, "b3"),
            new IceBlock(function, "b4"),
            new IceBlock(function, "b5"),
            new IceBlock(function, "b6"),
        };

        b[0].addSuccessor(b[1]);

        b[1].addSuccessor(b[2]);
        b[1].addSuccessor(b[3]);

        b[2].addSuccessor(b[4]);
        b[3].addSuccessor(b[4]);

        b[4].addSuccessor(b[5]);

        IceBlock[] expected = {null, b[0], b[1], b[1], b[1], b[4]};
        DominatorTree dominatorTree = new DominatorTree(function);
        for (int i = 0; i < b.length; i++) {
            IceBlock dom = dominatorTree.getDominator(b[i]);
            if (dom != expected[i]) {
                assert expected[i] != null;
                System.out.println("Test failed on " + b[i].getName() + ": expected " + expected[i].getName() + ", but got " + dom.getName());
                return;
            }
        }

        System.out.println("Test passed: all dominators are correct.");
    }
}

