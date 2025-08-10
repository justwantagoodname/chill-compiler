package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceConstantBoolean;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
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

        Log.d("Before:\n" + before + "\nAfter:\n" + actual);

        String expected = """
                define i32 @testFunction() {
                entry:
                \tret i32 0
                }""";

        assertEquals(expected, actual.toString());
    }

    private static IceFunction createUnusedParameter() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.VOID);
        var a = new IceFunction.IceFunctionParameter(function, "a", IceType.I32);
        function.addParameter(a);

        IceBlock entry = function.getEntryBlock();
        var add = new IceBinaryInstruction.Add(entry, "add", IceType.I32, a, new IceConstantInt(2));
        var sub = new IceBinaryInstruction.Sub(entry, "sub", IceType.I32, add, new IceConstantInt(1));
        var mul = new IceBinaryInstruction.Mul(entry, "mul", IceType.I32, add, new IceConstantInt(3));
        entry.addInstruction(add);
        entry.addInstruction(sub);
        entry.addInstruction(mul);

        IceInstruction ret = new IceRetInstruction(entry);
        entry.addInstruction(ret);

        return function;
    }

    @Test
    public void testSCCPWithUnusedParameter() {
        SmartChilletSimplifyCFG pass = new SmartChilletSimplifyCFG();
        IceFunction function = createUnusedParameter();

        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before + "\nAfter:\n" + actual);

        String expected =
                """
                        define void @testFunction(i32 %a) {
                        entry:
                        \tret void
                        }""";

        assertEquals(expected, actual.toString());
    }
}
