package top.voidc.optimizer.pass.unit;


import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.type.IceType;

import org.junit.jupiter.api.Test;
import top.voidc.optimizer.pass.function.RenameVariable;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class RenameVariableTest {
    private static IceUnit createFunction() {
        IceUnit unit = new IceUnit("testUnit");
        IceFunction function = new IceFunction("testFunction");
        function.setReturnType(IceType.VOID);

        IceBlock entry = function.getEntryBlock();
        IceBlock exit = function.getExitBlock();

        var add1 = new IceBinaryInstruction.Add(entry, "114514", IceType.I32, new IceConstantInt(1), new IceConstantInt(2));
        var add2 = new IceBinaryInstruction.Add(entry, "1919810", IceType.I32, new IceConstantInt(1), add1);
        entry.addInstruction(add1);
        entry.addInstruction(add2);

        unit.addFunction(function);
        return unit;
    }

    @Test
    public void testRenameVariable() {
        IceUnit unit = createFunction();
        RenameVariable pass = new RenameVariable();
        unit.getFunctions().forEach(pass::run);

        StringBuilder actual = new StringBuilder();
        unit.getTextIR(actual);

        String expected = """
                ; testUnit
                define void @testFunction() {
                entry:
                \t%0 = add i32 1, 2
                \t%1 = add i32 1, %0
                }
                """;

        assertEquals(expected, actual.toString());
    }
}
