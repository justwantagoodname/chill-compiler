package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IceInstruction.InstructionType;
import top.voidc.ir.ice.instruction.IceRetInstruction;
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
        entry.addInstruction(add);
        entry.addSuccessor(exit);

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
                "exit:\n" +
                "\tret i32 3\n" +
                "\n" +
                "}";
        assertEquals(expected, actual.toString());
    }
}
