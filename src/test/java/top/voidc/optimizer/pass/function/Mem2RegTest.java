package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;

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
}
