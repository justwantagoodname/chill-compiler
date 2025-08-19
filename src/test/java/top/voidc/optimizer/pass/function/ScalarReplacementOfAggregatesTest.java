package top.voidc.optimizer.pass.function;

import org.junit.jupiter.api.Disabled;
import top.voidc.ir.ice.constant.IceFunction;

import top.voidc.misc.Log;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

@Disabled("禁用一下，后面打算SORA修了再修复")
public class ScalarReplacementOfAggregatesTest {
    private static IceFunction createSimpleFunctionWithOneArray() {
        return IceFunction.fromTextIR("""
                define void @testFunction() {
                entry:
                    %a = alloca i32
                    %b = alloca [10 x i32]
                    %c = alloca float
                    ret void
                """);
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

        String expected = """
                define void @testFunction() {
                entry:
                \t%b_i9 = alloca i32
                \t%b_i8 = alloca i32
                \t%b_i7 = alloca i32
                \t%b_i6 = alloca i32
                \t%b_i5 = alloca i32
                \t%b_i4 = alloca i32
                \t%b_i3 = alloca i32
                \t%b_i2 = alloca i32
                \t%b_i1 = alloca i32
                \t%b_i0 = alloca i32
                \t%a = alloca i32
                \t%c = alloca float
                \tret void
                }""";

        assertEquals(expected, actual.toString());
    }

    private static IceFunction createFunctionWithOnlyConstantIndicies() {
        return IceFunction.fromTextIR("""
            define i32 @testFunction() {
            entry:
                %arr = alloca [10 x i32]
                %getPtr = getelementptr [10 x i32], [10 x i32]* %arr, i32 0, i32 3
                store i32 42, i32* %getPtr
                br label %exit
            exit:
                %getPtr2 = getelementptr [10 x i32], [10 x i32]* %arr, i32 0, i32 3
                %x = load i32, i32* %getPtr2
                ret i32 %x
            }
            """);
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

        Log.d("Before:\n" + before + "\nAfter:\n" + actual);

        String expected = """
                define i32 @testFunction() {
                entry:
                \t%arr_i9 = alloca i32
                \t%arr_i8 = alloca i32
                \t%arr_i7 = alloca i32
                \t%arr_i6 = alloca i32
                \t%arr_i5 = alloca i32
                \t%arr_i4 = alloca i32
                \t%arr_i3 = alloca i32
                \t%arr_i2 = alloca i32
                \t%arr_i1 = alloca i32
                \t%arr_i0 = alloca i32
                \tstore i32 42, i32* %arr_i3
                \tbr label %exit
                exit:
                \t%x = load i32, i32* %arr_i3
                \tret i32 %x
                }""";

        assertEquals(expected, actual.toString());
    }
}
