package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;

import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;

import top.voidc.misc.Log;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class Mem2RegTest {
    public static IceFunction createOneBlockFunction() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.VOID);
        IceBlock block = function.getEntryBlock();
        block.addInstruction(new IceAllocaInstruction(block, "a", IceType.I32));
        block.addInstruction(new IceAllocaInstruction(block, "b", IceType.I32));
        block.addInstruction(new IceAllocaInstruction(block, "c", IceType.F32));
        block.addInstruction(new IceAllocaInstruction(block, "d", new IceArrayType(IceType.I32, 10)));
        return function;
    }

    @Test
    public void testBasicMem2Reg() {
        IceFunction function = createOneBlockFunction();
        Mem2Reg pass = new Mem2Reg();

//        StringBuilder before = new StringBuilder();
//        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

//        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        // Check if the alloca instructions are removed
        String expected = """
                define void @testFunction() {
                entry:
                \t%d = alloca [10 x i32]
                }""";
        assertEquals(expected, actual.toString());
    }

    public static IceFunction createThreeBlocksFunction() {
        return IceFunction.fromTextIR("""
            define void @testFunction() {
            entry:
                %a = alloca i32
                br i1 true, label %block2, label %block3
            block2:
                store i32 2, i32* %a
                br label %merge
            block3:
                %add = add i32 1, 2
                store i32 %add, i32* %a
                br label %merge
            merge:
                ret void
            }
            """);
    }

    @Test
    public void testBasicMergeAlloca() {
        IceFunction function = createThreeBlocksFunction();
        Mem2Reg pass = new Mem2Reg();

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);
        Log.d(actual.toString());
        assertEquals(1, function.getEntryBlock().size());
    }

    private static IceFunction createComplexPhiFunction() {
        return IceFunction.fromTextIR("""
            define i32 @example(i32 %x, i32 %y) {
            entry:
                %z_ptr = alloca i32
                %i_ptr = alloca i32
                %cmp = icmp sgt i32 %x, 0
                br i1 %cmp, label %then, label %else
            then:
                %add = add i32 %x, %y
                store i32 %add, i32* %z_ptr
                br label %ifend
            else:
                %sub = sub i32 %x, %y
                store i32 %sub, i32* %z_ptr
                br label %ifend
            ifend:
                store i32 0, i32* %i_ptr
                br label %loop
            loop:
                %i_val = load i32, i32* %i_ptr
                %cond = icmp slt i32 %i_val, 5
                br i1 %cond, label %loop_body, label %after_loop
            loop_body:
                %z_val = load i32, i32* %z_ptr
                %z_new = add i32 %z_val, %i_val
                store i32 %z_new, i32* %z_ptr
                %i_next = add i32 %i_val, 1
                store i32 %i_next, i32* %i_ptr
                br label %loop
            after_loop:
                %final_z = load i32, i32* %z_ptr
                ret i32 %final_z
            }
            """);
    }

    @Test
    public void testFullMem2Reg() {
        IceFunction function = createComplexPhiFunction();
        Mem2Reg pass = new Mem2Reg();

//        StringBuilder before = new StringBuilder();
//        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

//        Log.d("Before:\n" + before.toString() + "\nAfter:\n" + actual.toString());

        String expected = """
            define i32 @example(i32 %x, i32 %y) {
            entry:
            	%cmp = icmp sgt i32 %x, 0
            	br i1 %cmp, label %then, label %else
            else:
            	%sub = sub i32 %x, %y
            	br label %ifend
            then:
            	%add = add i32 %x, %y
            	br label %ifend
            ifend:
            	%0 = phi i32 [ %sub, %else ], [ %add, %then ]
            	br label %loop
            loop:
            	%1 = phi i32 [ 0, %ifend ], [ %i_next, %loop_body ]
            	%2 = phi i32 [ %0, %ifend ], [ %z_new, %loop_body ]
            	%cond = icmp slt i32 %1, 5
            	br i1 %cond, label %loop_body, label %after_loop
            after_loop:
            	ret i32 %2
            loop_body:
            	%z_new = add i32 %2, %1
            	%i_next = add i32 %1, 1
            	br label %loop
            }""";
        assertEquals(expected, actual.toString());
    }

    private static IceFunction createFunctionRetValueNeverInitialized() {
        return IceFunction.fromTextIR("""
            define i32 @testFunction() {
            entry:
                %a = alloca i32
                br label %exit
            exit:
                %val = load i32, i32* %a
                ret i32 %val
            }
            """);
    }

    @Test
    public void testFunctionRetValueNeverInitialized() {
        IceFunction function = createFunctionRetValueNeverInitialized();
        Mem2Reg pass = new Mem2Reg();

        StringBuilder before = new StringBuilder();
        function.getTextIR(before);

        pass.run(function);

        StringBuilder actual = new StringBuilder();
        function.getTextIR(actual);

        Log.d("Before:\n" + before + "\nAfter:\n" + actual);

        String expected = """
                define i32 @testFunction() {
                entry:
                \tbr label %exit
                exit:
                \tret i32 undef
                }""";
        assertEquals(expected, actual.toString());
    }
}
