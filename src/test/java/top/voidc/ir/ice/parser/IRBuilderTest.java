package top.voidc.ir.ice.parser;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import org.junit.jupiter.api.Test;
import top.voidc.frontend.ir.TypeVisitor;
import top.voidc.frontend.ir.InstructionVisitor;
import top.voidc.frontend.parser.IceLexer;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceUndef;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.instruction.IceInstruction.InstructionType;
import top.voidc.ir.ice.instruction.IceCmpInstruction.CmpType;
import top.voidc.ir.ice.type.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.*;

public class IRBuilderTest {

    protected static Map<String, IceValue> env(Object... args) {
        Map<String, IceValue> map = new HashMap<>();
        for (int i = 0; i < args.length; i += 2) {
            map.put((String) args[i], (IceValue) args[i + 1]);
        }
        return map;
    }

    protected static IceParser buildIRParser(String textIR) {
        var irStream = CharStreams.fromString(textIR);
        var tokenStream = new CommonTokenStream(new IceLexer(irStream));
        return new IceParser(tokenStream);
    }

    private IceFunction createTestFunction() {
        var func = new IceFunction("test");
        func.setReturnType(IceType.VOID);
        return func;
    }

    private IceBlock createTestBlock(IceFunction func) {
        return new IceBlock(func, "test");
    }

    @Test
    public void testBlockBuilder() {
        var func = createTestFunction();
        
        // 测试带单条指令的块
        var blockWithInstr = IceBlock.fromTextIR("""
            %block1:
                %1 = alloca i32
                ret void
            """, func);
        assertEquals("block1", blockWithInstr.getName());
        assertEquals(2, blockWithInstr.getInstructions().size());
        assertTrue(blockWithInstr.getInstructions().get(0) instanceof IceAllocaInstruction);
        assertTrue(blockWithInstr.getInstructions().get(1) instanceof IceRetInstruction);
        
        // 测试带多条指令和跳转的块
        var nextBlock = new IceBlock(func, "next");
        var blockWithBranch = IceBlock.fromTextIR("""
            %block2:
                %ptr = alloca i32
                store i32 42, i32* %ptr
                %val = load i32, i32* %ptr
                br label %next
            """, func, env("next", nextBlock));
        
        assertEquals("block2", blockWithBranch.getName());
        assertEquals(4, blockWithBranch.getInstructions().size());
        assertTrue(blockWithBranch.getSuccessors().size() == 1);
        assertEquals(nextBlock, blockWithBranch.getSuccessors().get(0));
        
        // 测试条件分支块
        var thenBlock = new IceBlock(func, "then");
        var elseBlock = new IceBlock(func, "else"); 
        var blockWithCond = IceBlock.fromTextIR("""
            %block3:
                %cond = icmp slt i32 %a, %b
                br i1 %cond, label %then, label %else
            """, func, env(
                "a", IceConstantData.create(10),
                "b", IceConstantData.create(20),
                "then", thenBlock,
                "else", elseBlock
            ));
        
        assertEquals("block3", blockWithCond.getName());
        assertEquals(2, blockWithCond.getInstructions().size());
        assertTrue(blockWithCond.getSuccessors().contains(thenBlock));
        assertTrue(blockWithCond.getSuccessors().contains(elseBlock));
    }

    @Test
    public void testIceConstantParser() {
        final var intValue = new Random().nextInt();
        assertEquals(IceConstantData.create(intValue), IceConstantData.fromTextIR(String.valueOf(intValue)));

        final var floatValue = new Random().nextFloat();

        assertEquals(IceConstantData.create(floatValue), IceConstantData.fromTextIR(String.valueOf(floatValue)));

        assertEquals(IceConstantData.fromTextIR("true"), IceConstantData.create(true));

        assertEquals(IceConstantData.fromTextIR("false"), IceConstantData.create(false));
    }

    @Test
    public void testTypeParser() {
        assertEquals(IceType.I32, buildIRParser("i32").type().accept(new TypeVisitor()));
        assertEquals(IceType.F32, buildIRParser("float").type().accept(new TypeVisitor()));
        assertEquals(IceType.I1, buildIRParser("i1").type().accept(new TypeVisitor()));
        assertEquals(IceType.VOID, buildIRParser("void").type().accept(new TypeVisitor()));

        final var simpleArray = buildIRParser("[10 x i32]").type().accept(new TypeVisitor());
        assertEquals(new IceArrayType(IceType.I32, 10), simpleArray);

        final var complexArray = buildIRParser("[30 x [20 x [10 x i32]]]").type().accept(new TypeVisitor());
        assertEquals(complexArray, IceArrayType.buildNestedArrayType(List.of(30, 20, 10), IceType.I32));

        final var simplePointer = buildIRParser("i32*").type().accept(new TypeVisitor());
        assertEquals(new IcePtrType<>(IceType.I32), simplePointer);

        final var nestedPointer = buildIRParser("i32***").type().accept(new TypeVisitor());
        assertEquals(new IcePtrType<>(new IcePtrType<>(new IcePtrType<>(IceType.I32))), nestedPointer);

        final var complexPointer = buildIRParser("[10 x i32]**").type().accept(new TypeVisitor());
        assertEquals(new IcePtrType<>(new IcePtrType<>(new IceArrayType(IceType.I32, 10))), complexPointer);
    }

    @Test
    public void testInstructionParser() {
        // Test alloca instruction
        var func = createTestFunction();
        var block = createTestBlock(func);
        var allocaInstr = buildIRParser("%ptr = alloca i32, align 4")
                .instruction().accept(new InstructionVisitor(block, env()));
        assertInstanceOf(IceAllocaInstruction.class, allocaInstr);
        assertEquals(IceType.I32, ((IcePtrType<?>) allocaInstr.getType()).getPointTo());
        allocaInstr.destroy();

        // Test load instruction  
        func = createTestFunction();
        block = createTestBlock(func);
        var ptr1 = new IceAllocaInstruction(block, IceType.I32);
        var loadInstr = buildIRParser("%val = load i32, i32* %ptr")
                .instruction().accept(new InstructionVisitor(block, env("ptr", ptr1)));
        assertInstanceOf(IceLoadInstruction.class, loadInstr);
        ptr1.destroy();
        loadInstr.destroy();

        // Test store instruction
        func = createTestFunction();
        block = createTestBlock(func);
        var ptr2 = new IceAllocaInstruction(block, IceType.I32);
        var storeInstr = buildIRParser("store i32 42, i32* %ptr")
                .instruction().accept(new InstructionVisitor(block, env("ptr", ptr2)));
        assertInstanceOf(IceStoreInstruction.class, storeInstr);
        ptr2.destroy();
        storeInstr.destroy();

        // Test GEP instruction
        func = createTestFunction();
        block = createTestBlock(func);
        var arr = new IceAllocaInstruction(block, new IceArrayType(IceType.I32, 10));
        var gepInstr = buildIRParser("%arrayptr = getelementptr [10 x i32], [10 x i32]* %arr, i32 0, i32 5")
                .instruction().accept(new InstructionVisitor(block, env("arr", arr)));
        assertInstanceOf(IceGEPInstruction.class, gepInstr);
        arr.destroy();
        gepInstr.destroy();

        // Test arithmetic instruction
        func = createTestFunction();
        block = createTestBlock(func);
        var alloc1 = new IceAllocaInstruction(block, IceType.I32);
        var alloc2 = new IceAllocaInstruction(block, IceType.I32);
        var load1 = new IceLoadInstruction(block, alloc1);
        var load2 = new IceLoadInstruction(block, alloc2);
        var addInstr = buildIRParser("%result = add i32 %a, %b")
                .instruction().accept(new InstructionVisitor(block, env("a", load1, "b", load2)));
        assertInstanceOf(IceBinaryInstruction.class, addInstr);
        assertEquals(InstructionType.ADD, addInstr.getInstructionType());
        alloc1.destroy();
        alloc2.destroy();
        load1.destroy();
        load2.destroy();
        addInstr.destroy();

        // Test compare instruction
        func = createTestFunction();
        block = createTestBlock(func);
        alloc1 = new IceAllocaInstruction(block, IceType.I32);
        alloc2 = new IceAllocaInstruction(block, IceType.I32);
        load1 = new IceLoadInstruction(block, alloc1);
        load2 = new IceLoadInstruction(block, alloc2);
        var cmpInstr = buildIRParser("%cond = icmp slt i32 %x, %y")
                .instruction().accept(new InstructionVisitor(block, env("x", load1, "y", load2)));
        assertInstanceOf(IceIcmpInstruction.class, cmpInstr);
        assertEquals(CmpType.SLT, ((IceIcmpInstruction) cmpInstr).getCmpType());
        alloc1.destroy();
        alloc2.destroy();
        load1.destroy();
        load2.destroy();
        cmpInstr.destroy();

        // Test conversion instruction
        func = createTestFunction();
        block = createTestBlock(func);
        alloc1 = new IceAllocaInstruction(block, IceType.I32);
        load1 = new IceLoadInstruction(block, alloc1);
        var convInstr = buildIRParser("%float_val = sitofp i32 %int_val to float")
                .instruction().accept(new InstructionVisitor(block, env("int_val", load1)));
        assertInstanceOf(IceConvertInstruction.class, convInstr);
        alloc1.destroy();
        load1.destroy();
        convInstr.destroy();

        // Test branch instructions
        func = createTestFunction();
        block = createTestBlock(func);
        var targetBlock = new IceBlock(func, "label");
        var brInstr = buildIRParser("br label %label")
                .terminatorInstr().accept(new InstructionVisitor(block, env("label", targetBlock)));
        assertInstanceOf(IceBranchInstruction.class, brInstr);
        assertFalse(((IceBranchInstruction) brInstr).isConditional());
        brInstr.destroy();

        func = createTestFunction();
        block = createTestBlock(func);
        var thenBlock = new IceBlock(func, "then");
        var elseBlock = new IceBlock(func, "else");
        alloc1 = new IceAllocaInstruction(block, IceType.I1);
        load1 = new IceLoadInstruction(block, alloc1);
        var condBrInstr = buildIRParser("br i1 %cond, label %then, label %else")
                .terminatorInstr().accept(new InstructionVisitor(block, env(
                    "cond", load1,
                    "then", thenBlock,
                    "else", elseBlock
                )));
        assertInstanceOf(IceBranchInstruction.class, condBrInstr);
        assertTrue(((IceBranchInstruction) condBrInstr).isConditional());
        alloc1.destroy();
        load1.destroy();
        condBrInstr.destroy();

        // Test return instructions
        func = createTestFunction();
        block = createTestBlock(func);
        var retVoidInstr = buildIRParser("ret void")
                .terminatorInstr().accept(new InstructionVisitor(block, env()));
        assertInstanceOf(IceRetInstruction.class, retVoidInstr);
        assertTrue(((IceRetInstruction) retVoidInstr).isReturnVoid());
        retVoidInstr.destroy();

        func = createTestFunction();
        block = createTestBlock(func);
        alloc1 = new IceAllocaInstruction(block, IceType.I32);
        load1 = new IceLoadInstruction(block, alloc1);
        var retValInstr = buildIRParser("ret i32 %value")
                .terminatorInstr().accept(new InstructionVisitor(block, env("value", load1)));
        assertInstanceOf(IceRetInstruction.class, retValInstr);
        assertTrue(((IceRetInstruction) retValInstr).getReturnValue().isPresent());
        alloc1.destroy();
        load1.destroy();
        retValInstr.destroy();

        // Test phi instruction
        func = createTestFunction();
        block = createTestBlock(func);
        var bb1 = new IceBlock(func, "bb1");
        var bb2 = new IceBlock(func, "bb2");
        alloc1 = new IceAllocaInstruction(block, IceType.I32);
        alloc2 = new IceAllocaInstruction(block, IceType.I32);
        load1 = new IceLoadInstruction(block, alloc1);
        load2 = new IceLoadInstruction(block, alloc2);
        var phiInstr = buildIRParser("%result = phi i32 [%val1, %bb1], [%val2, %bb2]")
                .instruction().accept(new InstructionVisitor(block, env(
                    "val1", load1,
                    "val2", load2,
                    "bb1", bb1,
                    "bb2", bb2
                )));
        assertInstanceOf(IcePHINode.class, phiInstr);
        alloc1.destroy();
        alloc2.destroy();
        load1.destroy();
        load2.destroy();
        phiInstr.destroy();

        // Test unreachable instruction
        func = createTestFunction();
        block = createTestBlock(func);
        var unreachableInstr = buildIRParser("unreachable")
                .terminatorInstr().accept(new InstructionVisitor(block, env()));
        assertInstanceOf(IceUnreachableInstruction.class, unreachableInstr);
        unreachableInstr.destroy();

        // Test call instruction
        func = createTestFunction();
        block = createTestBlock(func);
        var calledFunc = new IceFunction("func");
        calledFunc.setReturnType(IceType.I32);
        alloc1 = new IceAllocaInstruction(block, IceType.I32);
        load1 = new IceLoadInstruction(block, alloc1);
        var callInstr = buildIRParser("%result = call i32 @func(i32 %arg)")
                .instruction().accept(new InstructionVisitor(block, env(
                    "func", calledFunc,
                    "arg", load1
                )));
        assertInstanceOf(IceCallInstruction.class, callInstr);
        alloc1.destroy();
        load1.destroy();
        callInstr.destroy();
    }
}
