package top.voidc.optimizer.pass.unit;


import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceType;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

public class RenameVariableTest {
    private static IceUnit createFunction() {
        IceUnit unit = new IceUnit("testUnit");
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.VOID);

        IceBlock entry = function.getEntryBlock();
        IceBlock exit = function.getExitBlock();

        IceInstruction add1 = new IceBinaryInstruction(entry, IceInstruction.InstructionType.ADD, IceType.I32, new IceConstantInt(1), new IceConstantInt(2));
        add1.setName("114514");
        IceInstruction add2 = new IceBinaryInstruction(entry, IceInstruction.InstructionType.ADD, IceType.I32, new IceConstantInt(1), add1);
        add2.setName("1919810");
        entry.addInstruction(add1);
        entry.addInstruction(add2);
        entry.addSuccessor(exit);

        unit.addFunction(function);
        return unit;
    }

    @Test
    public void testRenameVariable() {
        IceUnit unit = createFunction();
        RenameVariable pass = new RenameVariable();
        pass.run(unit);

        StringBuilder actual = new StringBuilder();
        unit.getTextIR(actual);

        String expected = "; testUnit\n" +
                "define void @testFunction() {\n" +
                "entry:\n" +
                "\t%0 = add i32 1, 2\n" +
                "\t%1 = add i32 1, %0\n" +
                "exit:\n" +
                "\n" +
                "}\n";

        assertEquals(expected, actual.toString());
    }
}
