package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
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
}
