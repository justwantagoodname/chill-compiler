package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.*;

import top.voidc.misc.Log;

import java.util.List;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class ScalarReplacementOfAggregatesTest {
    private static IceFunction createSimpleFunctionWithOneArray() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.VOID);
        IceBlock block = function.getEntryBlock();
        block.addInstruction(new IceAllocaInstruction(block, "a", IceType.I32));
        block.addInstruction(new IceAllocaInstruction(block, "b", new IceArrayType(IceType.I32, 10)));
        block.addInstruction(new IceAllocaInstruction(block, "c", IceType.F32));
        block.addSuccessor(function.getExitBlock());
        return function;
    }
    @Test
    public void testBasicSROA() {
        IceFunction function = createSimpleFunctionWithOneArray();
        ScalarReplacementOfAggregates pass = new ScalarReplacementOfAggregates();

//        StringBuilder before = new StringBuilder();
//        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

//        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = "define void @testFunction() {\n" + "entry:\n" + "\t%b_i9 = alloca i32\n"
            + "\t%b_i8 = alloca i32\n" + "\t%b_i7 = alloca i32\n" + "\t%b_i6 = alloca i32\n"
            + "\t%b_i5 = alloca i32\n" + "\t%b_i4 = alloca i32\n" + "\t%b_i3 = alloca i32\n"
            + "\t%b_i2 = alloca i32\n" + "\t%b_i1 = alloca i32\n" + "\t%b_i0 = alloca i32\n"
            + "\t%a = alloca i32\n" + "\t%c = alloca float\n" + "exit:\n" + "\n" + "}";

        assertEquals(expected, actual.toString());
    }

    private static IceFunction createFunctionWithOnlyConstantIndicies() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.I32);

        IceBlock entry = function.getEntryBlock();
        IceInstruction arr = new IceAllocaInstruction(entry, "arr", new IceArrayType(IceType.I32, 10));
        IceInstruction getPtr = new IceGEPInstruction(entry, arr, List.of(new IceConstantInt(0), new IceConstantInt(3)));
        IceInstruction store = new IceStoreInstruction(entry, getPtr, new IceConstantInt(42));
        entry.addInstruction(arr);
        entry.addInstruction(getPtr);
        entry.addInstruction(store);
        entry.addSuccessor(function.getExitBlock());

        IceBlock exit = function.getExitBlock();
        IceInstruction getPtr2 = new IceGEPInstruction(exit, arr, List.of(new IceConstantInt(0), new IceConstantInt(3)));
        IceInstruction x = new IceLoadInstruction(exit, "x", getPtr2);
        IceInstruction ret = new IceRetInstruction(exit, x);
        exit.addInstruction(getPtr2);
        exit.addInstruction(x);
        exit.addInstruction(ret);

        return function;
    }

    @Test
    public void testSROAWithConstantIndices() {
        IceFunction function = createFunctionWithOnlyConstantIndicies();
        ScalarReplacementOfAggregates pass = new ScalarReplacementOfAggregates();

        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = "define i32 @testFunction() {\n" + "entry:\n" + "\t%arr_i9 = alloca i32\n"
            + "\t%arr_i8 = alloca i32\n" + "\t%arr_i7 = alloca i32\n" + "\t%arr_i6 = alloca i32\n"
            + "\t%arr_i5 = alloca i32\n" + "\t%arr_i4 = alloca i32\n" + "\t%arr_i3 = alloca i32\n"
            + "\t%arr_i2 = alloca i32\n" + "\t%arr_i1 = alloca i32\n" + "\t%arr_i0 = alloca i32\n"
            + "\tstore i32 42, i32* %arr_i3\n" + "exit:\n" + "\t%x = load i32, i32* %arr_i3\n"
            + "\tret i32 %x\n" + "\n" + "}";

        assertEquals(expected, actual.toString());
    }
}
