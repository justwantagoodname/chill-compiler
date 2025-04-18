package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.instruction.IceInstruction.InstructionType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class SparseConditionalConstantPropagationTest {
    private static IceFunction createSimpleFunction() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.I32);

        IceBlock entry = function.getEntryBlock();
        IceBlock exit = function.getExitBlock();
        IceInstruction add = new IceBinaryInstruction(entry, InstructionType.ADD, "add", IceType.I32,
                new IceConstantInt(1), new IceConstantInt(2));
        IceInstruction icmp1 = new IceIcmpInstruction(entry, IceIcmpInstruction.CmpType.EQ,
                new IceConstantInt(1), new IceConstantInt(2));
        IceInstruction icmp2 = new IceIcmpInstruction(entry, IceIcmpInstruction.CmpType.EQ,
                new IceConstantInt(3), new IceConstantInt(3));
        IceInstruction outEntry = new IceBranchInstruction(entry, icmp1, exit, exit);
        entry.addInstruction(add);
        entry.addInstruction(icmp1);
        entry.addInstruction(icmp2);
        entry.addInstruction(outEntry);

        IceInstruction ret = new IceRetInstruction(exit, add);
        exit.addInstruction(ret);

        return function;
    }

    @Test
    public void testSimpleSCCP() {
        SparseConditionalConstantPropagation pass = new SparseConditionalConstantPropagation();
        IceFunction function = createSimpleFunction();

        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = "define i32 @testFunction() {\n" +
                "entry:\n" +
                "\tbr label %exit\n" +
                "exit:\n" +
                "\tret i32 3\n" +
                "\n" +
                "}";
        assertEquals(expected, actual.toString());
    }

    private static IceFunction createComplexFunction() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.I32);
        IceValue a = new IceValue("a", IceType.I32);
        IceValue b = new IceValue("b", IceType.I32);
        function.addParameter(a);
        function.addParameter(b);

        IceBlock entry = function.getEntryBlock();
        IceBlock block1 = new IceBlock(function, "block1");
        IceBlock block2 = new IceBlock(function, "block2");
        IceBlock exit = function.getExitBlock();

        IceInstruction icmp1 = new IceIcmpInstruction(entry, IceIcmpInstruction.CmpType.EQ,
                new IceConstantInt(1), new IceConstantInt(2));
        IceInstruction outEntry = new IceBranchInstruction(entry, icmp1, block1, block2);
        entry.addInstruction(icmp1);
        entry.addInstruction(outEntry);

        IceInstruction sub = new IceBinaryInstruction(block1, InstructionType.SUB, "sub", IceType.I32,
                a, b);
        IceInstruction outBlock1 = new IceBranchInstruction(block1, exit);
        block1.addInstruction(sub);
        block1.addInstruction(outBlock1);

        IceInstruction add = new IceBinaryInstruction(block2, InstructionType.ADD, "add", IceType.I32,
                new IceConstantInt(1), new IceConstantInt(2));
        IceInstruction outBlock2 = new IceBranchInstruction(block2, exit);
        block2.addInstruction(add);
        block2.addInstruction(outBlock2);

        IcePHINode phi = new IcePHINode(exit, "phi", IceType.I32);
        phi.addBranch(block1, sub);
        phi.addBranch(block2, add);
        exit.addInstruction(phi);
        IceInstruction ret = new IceRetInstruction(exit, phi);
        exit.addInstruction(ret);

        return function;
    }

    @Test
    public void testComplexSCCP() {
        SparseConditionalConstantPropagation pass = new SparseConditionalConstantPropagation();
        IceFunction function = createComplexFunction();

        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = "define i32 @testFunction(i32 %a, i32 %b) {\n" +
                "entry:\n" +
                "\tbr label %block2\n" +
                "block2:\n" +
                "\tbr label %exit\n" +
                "exit:\n" +
                "\tret i32 3\n" +
                "\n" +
                "}";
        assertEquals(expected, actual.toString());
    }

    private static IceFunction createReallyComplexBranch() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.I32);
        IceValue a = new IceValue("a", IceType.I32);
        IceValue b = new IceValue("b", IceType.I32);
        function.addParameter(a);
        function.addParameter(b);

        IceBlock entry = function.getEntryBlock();
        IceBlock block1 = new IceBlock(function, "block1");
        IceBlock block2 = new IceBlock(function, "block2");
        IceBlock exit = function.getExitBlock();

        IceInstruction icmp1 = new IceIcmpInstruction(entry, IceIcmpInstruction.CmpType.EQ,
                new IceConstantInt(1), new IceConstantInt(2));
        IceInstruction outEntry = new IceBranchInstruction(entry, icmp1, block1, block2);
        entry.addInstruction(icmp1);
        entry.addInstruction(outEntry);

        IceInstruction sub = new IceBinaryInstruction(block2, InstructionType.SUB, "sub", IceType.I32,
                a, new IceConstantInt(1));
        IceInstruction outBlock1 = new IceBranchInstruction(block1, exit);
        block1.addInstruction(sub);
        block1.addInstruction(outBlock1);

        IceInstruction add = new IceBinaryInstruction(block1, InstructionType.ADD, "add", IceType.I32,
                b, new IceConstantInt(2));
        IceInstruction outBlock2 = new IceBranchInstruction(block2, exit);
        block2.addInstruction(add);
        block2.addInstruction(outBlock2);

        IcePHINode phi = new IcePHINode(exit, "phi", IceType.I32);
        phi.addBranch(block1, sub);
        phi.addBranch(block2, add);
        exit.addInstruction(phi);
        IceInstruction ret = new IceRetInstruction(exit, phi);
        exit.addInstruction(ret);

        return function;
    }

    @Test
    public void testSCCPWillNotDeletePhiNodeIncorrectly() {
        SparseConditionalConstantPropagation pass = new SparseConditionalConstantPropagation();
        IceFunction function = createReallyComplexBranch();

        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = "define i32 @testFunction(i32 %a, i32 %b) {\n" +
                "entry:\n" +
                "\tbr label %block2\n" +
                "block2:\n" +
                "\t%add = add i32 %b, 2\n" +
                "\tbr label %exit\n" +
                "exit:\n" +
                "\tret i32 %add\n" +
                "\n" +
                "}";

        assertEquals(expected, actual.toString());
    }
}
