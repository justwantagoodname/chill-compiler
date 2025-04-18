package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceConstantBoolean;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.type.IceType;

import org.junit.jupiter.api.Test;
import top.voidc.misc.Log;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SmartChilletSimplifyCFGTest {
    /**
     * entry
     *   |
     *   A
     *  / \
     * B   C
     *  \ /
     *  exit
     * @return
     */
    private static IceFunction createSimpleDiamondCFG() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.I32);

        IceBlock entry = function.getEntryBlock();
        IceBlock blockA = new IceBlock(function, "blockA");
        IceBlock blockB = new IceBlock(function, "blockB");
        IceBlock blockC = new IceBlock(function, "blockC");
        IceBlock exit = function.getExitBlock();

        entry.addInstruction(new IceBranchInstruction(entry, blockA));
        IceConstantBoolean cond = new IceConstantBoolean(true);
        blockA.addInstruction(new IceBranchInstruction(blockA, cond, blockB, blockC));
        blockB.addInstruction(new IceBranchInstruction(blockB, exit));
        blockC.addInstruction(new IceBranchInstruction(blockC, exit));
        exit.addInstruction(new IceRetInstruction(exit, new IceConstantInt(0)));

        return function;
    }

    @Test
    public void testSCSCFGOnSimpleDiamondCFG() {
        IceFunction function = createSimpleDiamondCFG();
        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        SmartChilletSimplifyCFG pass = new SmartChilletSimplifyCFG();
        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = "define i32 @testFunction() {\n" +
                "entry:\n" +
                "\tbr label %blockA\n" +
                "blockA:\n" +
                "\tbr i1 true, label %blockB, label %blockC\n" +
                "blockB:\n" +
                "\tbr label %exit\n" +
                "blockC:\n" +
                "\tbr label %exit\n" +
                "exit:\n" +
                "\tret i32 0\n" +
                "}";

        assertEquals(expected, actual.toString());
    }
}
