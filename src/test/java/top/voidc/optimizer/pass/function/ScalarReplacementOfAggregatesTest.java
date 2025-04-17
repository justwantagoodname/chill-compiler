package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.*;
import top.voidc.optimizer.pass.Pass;

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
        Pass<IceFunction> pass = new ScalarReplacementOfAggregates();

//        StringBuilder before = new StringBuilder();
//        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

//        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = """
            define void @testFunction() {
            entry:
            	%b.i9 = alloca i32
            	%b.i8 = alloca i32
            	%b.i7 = alloca i32
            	%b.i6 = alloca i32
            	%b.i5 = alloca i32
            	%b.i4 = alloca i32
            	%b.i3 = alloca i32
            	%b.i2 = alloca i32
            	%b.i1 = alloca i32
            	%b.i0 = alloca i32
            	%b.i9 = alloca i32
            	%b.i8 = alloca i32
            	%b.i7 = alloca i32
            	%b.i6 = alloca i32
            	%b.i5 = alloca i32
            	%b.i4 = alloca i32
            	%b.i3 = alloca i32
            	%b.i2 = alloca i32
            	%b.i1 = alloca i32
            	%b.i0 = alloca i32
            	%a = alloca i32
            	%c = alloca float
            exit:
            
            }""";

        assertEquals(expected, actual.toString());
    }

    private static IceFunction createFunctionWithOnlyConstantIndicies() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.I32);

        IceBlock entry = function.getEntryBlock();
        IceInstruction arr = new IceAllocaInstruction(entry, "arr", new IceArrayType(IceType.I32, 10));
        IceInstruction getPtr = new IceGEPInstruction(entry, arr, List.of(new IceConstantInt(3)));
        IceInstruction store = new IceStoreInstruction(entry, getPtr, new IceConstantInt(42));
        entry.addInstruction(arr);
        entry.addInstruction(getPtr);
        entry.addInstruction(store);
        entry.addSuccessor(function.getExitBlock());

        IceBlock exit = function.getExitBlock();
        IceInstruction getPtr2 = new IceGEPInstruction(exit, arr, List.of(new IceConstantInt(3)));
        IceInstruction x = new IceLoadInstruction(exit, "x", getPtr2);
        IceInstruction ret = new IceRetInstruction(exit, x);
        exit.addInstruction(getPtr2);
        exit.addInstruction(x);
        exit.addInstruction(ret);

        return function;
    }

    @Test
    public void testSROAWithConstantIndicies() {
        IceFunction function = createFunctionWithOnlyConstantIndicies();
        Pass<IceFunction> pass = new ScalarReplacementOfAggregates();

        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = """
            define void @testFunction() {
            entry:
            	%arr.i9 = alloca i32
            	%arr.i8 = alloca i32
            	%arr.i7 = alloca i32
            	%arr.i6 = alloca i32
            	%arr.i5 = alloca i32
            	%arr.i4 = alloca i32
            	%arr.i3 = alloca i32
            	%arr.i2 = alloca i32
            	%arr.i1 = alloca i32
            	%arr.i0 = alloca i32
            	store i32 42, i32* %arr.i3
            exit:
            
            }""";

        assertEquals(expected, actual.toString());
    }
}
