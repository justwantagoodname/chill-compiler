package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.instruction.IceInstruction.InstructionType;
import top.voidc.ir.ice.instruction.IceCmpInstruction.CmpType;
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

        StringBuilder sb = new StringBuilder();
        function.getTextIR(sb);
        System.out.println(sb);
        pass.run(function);
    }

    public static IceFunction createThreeBlocksFunction() {
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.VOID);
        IceBlock block1 = function.getEntryBlock();
        IceBlock block2 = new IceBlock(function, "block2");
        IceBlock block3 = new IceBlock(function, "block3");
        IceBlock block4 = new IceBlock(function, "merge");
        block1.addSuccessor(block2);
        block1.addSuccessor(block3);
        block2.addSuccessor(block4);
        block3.addSuccessor(block4);

        IceInstruction add = new IceBinaryInstruction(block3, IceInstruction.InstructionType.ADD, IceType.I32, new IceConstantInt(1), new IceConstantInt(2));
        add.setName("add");
        block3.addInstruction(add);

        IceInstruction alloca = new IceAllocaInstruction(block1, "a", IceType.I32);
        block1.addInstruction(alloca);
        block2.addInstruction(new IceStoreInstruction(block2, alloca, new IceConstantInt(2)));
        block3.addInstruction(new IceStoreInstruction(block3, alloca, add));

        return function;
    }

    @Test
    public void testBasicMergeAlloca() {
        IceFunction function = createThreeBlocksFunction();
        Mem2Reg pass = new Mem2Reg();

        StringBuilder sb = new StringBuilder();
        sb.append("Before:\n");
        function.getTextIR(sb);

        pass.run(function);

        sb.append("\nAfter:\n");
        function.getTextIR(sb);

        System.out.println(sb);

        // Check if the alloca instruction is removed
        assertEquals(0, function.getEntryBlock().getInstructions().size());
    }

    private static IceFunction createComplexPhiFunction() {
        IceFunction function = new IceFunction("example");
        function.setReturnType(IceType.I32);
        IceValue x = new IceValue("x", IceType.I32);
        IceValue y = new IceValue("y", IceType.I32);
        function.addParameter(x);
        function.addParameter(y);

        IceBlock entry = function.getEntryBlock();
        IceBlock thenBlock = new IceBlock(function, "then");
        IceBlock elseBlock = new IceBlock(function, "else");
        IceBlock ifEndBlock = new IceBlock(function, "ifend");
        IceBlock loopBlock = new IceBlock(function, "loop");
        IceBlock loopBodyBlock = new IceBlock(function, "loop_body");
        IceBlock afterLoopBlock = new IceBlock(function, "after_loop");
        IceBlock exit = function.getExitBlock();

        // entry:
        IceInstruction z_ptr = new IceAllocaInstruction(entry, "z_ptr", IceType.I32);
        IceInstruction i_ptr = new IceAllocaInstruction(entry, "i_ptr", IceType.I32);
        IceInstruction cmp = new IceIcmpInstruction(entry, "cmp", CmpType.SGT, x, new IceConstantInt(0));
        IceInstruction outEntry = new IceBranchInstruction(entry, cmp, thenBlock, elseBlock);
        entry.addInstruction(z_ptr);
        entry.addInstruction(i_ptr);
        entry.addInstruction(cmp);
        entry.addInstruction(outEntry);

        // then:
        IceInstruction add = new IceBinaryInstruction(thenBlock, InstructionType.ADD, "add", IceType.I32, x, y);
        IceInstruction store = new IceStoreInstruction(thenBlock, z_ptr, add);
        IceInstruction outThen = new IceBranchInstruction(thenBlock, ifEndBlock);
        thenBlock.addInstruction(add);
        thenBlock.addInstruction(store);
        thenBlock.addInstruction(outThen);

        // else:
        IceInstruction sub = new IceBinaryInstruction(elseBlock, InstructionType.SUB, "sub", IceType.I32, x, y);
        IceInstruction store2 = new IceStoreInstruction(elseBlock, z_ptr, sub);
        IceInstruction outElse = new IceBranchInstruction(elseBlock, ifEndBlock);
        elseBlock.addInstruction(sub);
        elseBlock.addInstruction(store2);
        elseBlock.addInstruction(outElse);

        // ifend:
        IceInstruction store3 = new IceStoreInstruction(ifEndBlock, i_ptr, new IceConstantInt(0));
        IceInstruction outIfEnd = new IceBranchInstruction(ifEndBlock, loopBlock);
        ifEndBlock.addInstruction(store3);
        ifEndBlock.addInstruction(outIfEnd);

        // loop:
        IceInstruction i_val = new IceLoadInstruction(loopBlock, "i_val", i_ptr);
        IceInstruction cond = new IceIcmpInstruction(loopBlock, "cond", CmpType.SLT, i_val, new IceConstantInt(5));
        IceInstruction outLoop = new IceBranchInstruction(loopBlock, cond, loopBodyBlock, afterLoopBlock);
        loopBlock.addInstruction(i_val);
        loopBlock.addInstruction(cond);
        loopBlock.addInstruction(outLoop);

        // loop_body:
        IceInstruction z_val = new IceLoadInstruction(loopBodyBlock, "z_val", z_ptr);
        IceInstruction z_new = new IceBinaryInstruction(loopBodyBlock, InstructionType.ADD, "z_new", IceType.I32, z_val, i_val);
        IceInstruction store4 = new IceStoreInstruction(loopBodyBlock, z_ptr, z_new);
        IceInstruction i_next = new IceBinaryInstruction(loopBodyBlock, InstructionType.ADD, "i_next", IceType.I32, i_val, new IceConstantInt(1));
        IceInstruction store5 = new IceStoreInstruction(loopBodyBlock, i_ptr, i_next);
        IceInstruction outLoopBody = new IceBranchInstruction(loopBodyBlock, loopBlock);
        loopBodyBlock.addInstruction(z_val);
        loopBodyBlock.addInstruction(z_new);
        loopBodyBlock.addInstruction(store4);
        loopBodyBlock.addInstruction(i_next);
        loopBodyBlock.addInstruction(store5);
        loopBodyBlock.addInstruction(outLoopBody);

        // after_loop:
        afterLoopBlock.addSuccessor(exit);

        // exit:
        IceInstruction final_z = new IceLoadInstruction(afterLoopBlock, "final_z", z_ptr);
        IceInstruction ret = new IceRetInstruction(afterLoopBlock, final_z);
        exit.addInstruction(final_z);
        exit.addInstruction(ret);

        return function;
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

//        Log.d("Before:\n" + before.toString() + "After:\n" + actual.toString());

        String expected = "define i32 @example(i32 %x, i32 %y) {\n" + "entry:\n"
            + "\t%cmp = icmp sgt i32 %x, i32 0\n" + "\tbr i1 %cmp, label %then, label %else\n"
            + "then:\n" + "\t%add = add i32 i32 %x, i32 %y\n" + "\tbr label %ifend\n" + "else:\n"
            + "\t%sub = sub i32 i32 %x, i32 %y\n" + "\tbr label %ifend\n" + "ifend:\n"
            + "\t%z_ptr.1 = phi i32 [ i32 %sub, label %else ], [ i32 %add, label %then ]\n"
            + "\tbr label %loop\n" + "loop:\n"
            + "\t%i_ptr.3 = phi i32 [ i32 0, label %ifend ], [ i32 %i_next, label %loop_body ]\n"
            + "\t%z_ptr.4 = phi i32 [ i32 %z_ptr.1, label %ifend ], [ i32 %z_new, label %loop_body ]\n"
            + "\t%cond = icmp slt i32 %i_ptr.3, i32 5\n"
            + "\tbr i1 %cond, label %loop_body, label %after_loop\n" + "loop_body:\n"
            + "\t%z_new = add i32 i32 %z_ptr.4, i32 %i_ptr.3\n"
            + "\t%i_next = add i32 i32 %i_ptr.3, i32 1\n" + "\tbr label %loop\n" + "after_loop:\n"
            + "exit:\n" + "\tret i32 %z_ptr.4\n" + "\n" + "}";
        assertEquals(expected, actual.toString());
    }
}
