package top.voidc.frontend.ir;

import top.voidc.frontend.parser.IceBaseVisitor;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;

import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class InstructionVisitor extends IceBaseVisitor<IceInstruction> {
    private final IceBlock block;
    private final Map<String, IceValue> environment;
    private final TypeVisitor typeVisitor;

    private void putValue(String name, IceValue value) {
        String storeName = name;
        if (name.startsWith("%") || name.startsWith("@")) {
            storeName = name.substring(1);
        }
        environment.put(storeName, value);
    }

    public InstructionVisitor(IceBlock block, Map<String, IceValue> table) {
        this.block = block;
        this.environment = table;
        this.typeVisitor = new TypeVisitor(); // Instantiate TypeVisitor
    }

    // Helper to parse constant values
    private IceValue parseConstant(IceParser.ConstantContext ctx) {
        return new ConstantVisitor().visit(ctx);
    }

    // Helper to get IceValue from symbol table or parse constant
    private IceValue lookupValue(String name) {
        String lookupName = name;
        if (name.startsWith("%") || name.startsWith("@")) {
            lookupName = name.substring(1);
        }
        Optional<IceValue> value = Optional.ofNullable(environment.get(lookupName));
        Log.should(value.isPresent(), "Value " + name + " not found in symbol table");
        return value.get();
    }

    private String getPureName(String ident) {
        if (ident.startsWith("%") || ident.startsWith("@")) {
            return ident.substring(1);
        }
        return ident;
    }

    private IceValue getValue(IceParser.ValueContext ctx) {
        if (ctx.constant() != null) {
            return parseConstant(ctx.constant());
        } else if (ctx.IDENTIFIER() != null) {
            return lookupValue(ctx.IDENTIFIER().getText());
        } else if (ctx.GLOBAL_IDENTIFIER() != null) {
            return lookupValue(ctx.GLOBAL_IDENTIFIER().getText());
        }
        throw new IllegalArgumentException("Unknown value context: " + ctx.getText());
    }

    // Helper to get pointer value
    private IceValue getPointer(IceParser.PointerContext ctx) {
        String name = ctx.getText(); // IDENTIFIER or GLOBAL_IDENTIFIER
        return lookupValue(name);
    }

    @Override
    public IceInstruction visitAllocaInstr(IceParser.AllocaInstrContext ctx) {
        String resultReg = ctx.IDENTIFIER().getText();
        IceType allocatedType = typeVisitor.visit(ctx.type());
        // TODO: Handle alignment if present ctx.NUMBER()
        IceAllocaInstruction allocaInst = new IceAllocaInstruction(block, getPureName(resultReg), allocatedType);
        putValue(resultReg, allocaInst);
        return allocaInst;
    }

    @Override
    public IceInstruction visitLoadInstr(IceParser.LoadInstrContext ctx) {
        String resultReg = ctx.IDENTIFIER().getText();
        IceType resultType = typeVisitor.visit(ctx.type(0)); // Type of the value being loaded
        IceType pointerType = typeVisitor.visit(ctx.type(1)); // Type of the pointer
        IceValue pointer = getPointer(ctx.pointer());
        // TODO: Handle alignment if present ctx.NUMBER()
        // Type check (optional but recommended)
        // Log.should(pointer.getType().equals(pointerType), "Pointer type mismatch in load");
        // Log.should(pointerType.isPointerType() && ((IcePointerType)pointerType).getElementType().equals(resultType), "Load type mismatch");

        IceLoadInstruction loadInst = new IceLoadInstruction(block, getPureName(resultReg), pointer);
        putValue(resultReg, loadInst);
        return loadInst;
    }

    @Override
    public IceInstruction visitStoreInstr(IceParser.StoreInstrContext ctx) {
        IceType valueType = typeVisitor.visit(ctx.type(0));
        IceValue value = getValue(ctx.value());
        IceType pointerType = typeVisitor.visit(ctx.type(1));
        IceValue pointer = getPointer(ctx.pointer());
        // TODO: Handle alignment if present ctx.NUMBER()

        // Type checks
        // Log.should(value.getType().equals(valueType), "Store value type mismatch");
        // Log.should(pointer.getType().equals(pointerType), "Store pointer type mismatch");
        // Log.should(pointerType.isPointerType() && ((IcePointerType)pointerType).getElementType().equals(valueType), "Store type mismatch");

        return new IceStoreInstruction(block, pointer, value);
    }

    @Override
    public IceInstruction visitUnconditionalBranch(IceParser.UnconditionalBranchContext ctx) {
        String targetLabel = ctx.IDENTIFIER().getText();
        // Need a way to resolve label names to IceBlock objects.
        // This might happen in a later pass or require passing block map.
        // Placeholder: Assume block map exists or label resolution happens later.
//        Optional<IceValue> targetBlock = Optional.ofNullable(environment.get(targetLabel));
        final var targetBlock = lookupValue(targetLabel);
        Log.should(targetBlock instanceof IceBlock, "unconditional branch target block should be IceBlock");
        return new IceBranchInstruction(block, (IceBlock) targetBlock);
    }

    @Override
    public IceInstruction visitConditionalBranch(IceParser.ConditionalBranchContext ctx) {
        IceType condType = typeVisitor.visit(ctx.type());
        IceValue condition = getValue(ctx.value());
        String trueLabel = ctx.IDENTIFIER(0).getText();
        String falseLabel = ctx.IDENTIFIER(1).getText();

        // Log.should(condition.getType().equals(condType) && condType.isIntegerType(1), "Branch condition must be i1");

        var trueBlock = lookupValue(trueLabel);
        var falseBlock = lookupValue(falseLabel);
        Log.should(trueBlock instanceof IceBlock, "conditional branch target block should be IceBlock");
        Log.should(falseBlock instanceof IceBlock, "conditional branch target block should be IceBlock");

        return new IceBranchInstruction(block, condition, (IceBlock) trueBlock, (IceBlock) falseBlock);
    }

    @Override
    public IceInstruction visitVoidReturn(IceParser.VoidReturnContext ctx) {
        return new IceRetInstruction(block); // Return void
    }

    @Override
    public IceInstruction visitValueReturn(IceParser.ValueReturnContext ctx) {
        IceType returnType = typeVisitor.visit(ctx.type());
        IceValue returnValue = getValue(ctx.value());
        // Log.should(returnValue.getType().equals(returnType), "Return value type mismatch");
        return new IceRetInstruction(block, returnValue);
    }

    @Override
    public IceInstruction visitArithmeticInstr(IceParser.ArithmeticInstrContext ctx) {
        String resultReg = ctx.IDENTIFIER().getText();
        String op = ctx.binOp().getText().toUpperCase(); // e.g., ADD, SUB
        IceType type = typeVisitor.visit(ctx.type());
        IceValue lhs = getValue(ctx.value(0));
        IceValue rhs = getValue(ctx.value(1));

        // Log.should(lhs.getType().equals(type) && rhs.getType().equals(type), "Arithmetic operand type mismatch");

        IceBinaryInstruction binaryInst = switch (op) {
            case "ADD" -> new IceBinaryInstruction.Add(block, getPureName(resultReg), type, lhs, rhs);
            case "FADD" -> new IceBinaryInstruction.FAdd(block, getPureName(resultReg), type, lhs, rhs);
            case "SUB" -> new IceBinaryInstruction.Sub(block, getPureName(resultReg), type, lhs, rhs);
            case "FSUB" -> new IceBinaryInstruction.FSub(block, getPureName(resultReg), type, lhs, rhs);
            case "MUL" -> new IceBinaryInstruction.Mul(block, getPureName(resultReg), type, lhs, rhs);
            case "FMUL" -> new IceBinaryInstruction.FMul(block, getPureName(resultReg), type, lhs, rhs);
            case "DIV" -> new IceBinaryInstruction.Div(block, getPureName(resultReg), type, lhs, rhs);
            case "SDIV" -> new IceBinaryInstruction.SDiv(block, getPureName(resultReg), type, lhs, rhs);
            case "FDIV" -> new IceBinaryInstruction.FDiv(block, getPureName(resultReg), type, lhs, rhs);
            case "MOD" -> new IceBinaryInstruction.Mod(block, getPureName(resultReg), type, lhs, rhs);
            case "SHL" -> new IceBinaryInstruction.Shl(block, getPureName(resultReg), type, lhs, rhs);
            case "SHR" -> new IceBinaryInstruction.Shr(block, getPureName(resultReg), type, lhs, rhs);
            case "AND" -> new IceBinaryInstruction.And(block, getPureName(resultReg), type, lhs, rhs);
            case "OR" -> new IceBinaryInstruction.Or(block, getPureName(resultReg), type, lhs, rhs);
            case "XOR" -> new IceBinaryInstruction.Xor(block, getPureName(resultReg), type, lhs, rhs);
            default -> throw new IllegalArgumentException("Unknown binary operator: " + op);
        };
        putValue(resultReg, binaryInst);
        return binaryInst;
    }

    @Override
    public IceInstruction visitCallInstr(IceParser.CallInstrContext ctx) {
        String funcName = ctx.GLOBAL_IDENTIFIER().getText(); // Last IDENTIFIER is function name
        final var func = lookupValue(funcName);
        Log.should(func instanceof IceFunction, "Function " + funcName + " not found");

        List<IceValue> args = new ArrayList<>();
        if (ctx.argList() != null) {
            for (int i = 0; i < ctx.argList().value().size(); i++) {
                IceType argType = typeVisitor.visit(ctx.argList().type(i));
                IceValue argValue = getValue(ctx.argList().value(i));
                // Log.should(argValue.getType().equals(argType), "Call argument type mismatch for @" + funcName);
                args.add(argValue);
            }
        }

        // TODO: Verify argument count and types against function signature func.get().getFunctionType()

        if (ctx.IDENTIFIER() != null) { // Result = call ...
            String resultReg = ctx.IDENTIFIER().getText();
            IceType returnType = typeVisitor.visit(ctx.type());
            // Log.should(func.get().getReturnType().equals(returnType), "Call return type mismatch for @" + funcName);
            IceCallInstruction callInst = new IceCallInstruction(block, getPureName(resultReg), (IceFunction) func, args);
            putValue(resultReg, callInst);
            return callInst;
        } else { // call void ...
            // Log.should(func.get().getReturnType().isVoidType(), "Call return type mismatch for void function @" + funcName);
            return new IceCallInstruction(block, (IceFunction) func, args);
        }
    }

    @Override
    public IceInstruction visitGetElementPtrInstr(IceParser.GetElementPtrInstrContext ctx) {
        String resultReg = ctx.IDENTIFIER().getText();
        IceType baseType = typeVisitor.visit(ctx.type(0)); // The base type being indexed into
        IceType pointerType = typeVisitor.visit(ctx.type(1)); // The pointer type
        IceValue pointer = getPointer(ctx.pointer());

        // Log.should(pointer.getType().equals(pointerType), "GEP pointer type mismatch");
        // Log.should(pointerType.isPointerType() && ((IcePointerType)pointerType).getElementType().equals(baseType), "GEP base type mismatch");

        List<IceValue> indices = new ArrayList<>();
        for (int i = 2; i < ctx.type().size(); i++) { // Indices start from the 3rd type/value pair
            IceType indexType = typeVisitor.visit(ctx.type(i));
            IceValue indexValue = getValue(ctx.value(i - 2)); // value index is offset by 2 relative to type index
            // Log.should(indexValue.getType().equals(indexType) && indexType.isIntegerType(), "GEP index must be integer");
            indices.add(indexValue);
        }

        // The result type of GEP is tricky, it depends on the base type and indices.
        // Usually calculated within the GEP instruction constructor or a helper.
        IceGEPInstruction gepInst = new IceGEPInstruction(block, getPureName(resultReg), pointer, indices);
        putValue(resultReg, gepInst);
        return gepInst;
    }

    @Override
    public IceInstruction visitPhiInstr(IceParser.PhiInstrContext ctx) {
        String resultReg = ctx.IDENTIFIER(0).getText();
        IceType type = typeVisitor.visit(ctx.type());
        IcePHINode phiNode = new IcePHINode(block, getPureName(resultReg), type);

        for (int i = 0; i < ctx.value().size(); i++) {
            IceValue value = getValue(ctx.value(i));
            String label = ctx.IDENTIFIER(i + 1).getText(); // Labels start from the second IDENTIFIER
            // Log.should(value.getType().equals(type), "PHI node incoming value type mismatch");

            // Need to resolve label to predecessor block
            var predBlock = lookupValue(label);
            Log.should(predBlock instanceof IceBlock, "Predicate " + label + " not found");
            phiNode.addBranch((IceBlock) predBlock, value);
        }

        putValue(resultReg, phiNode);
        return phiNode;
    }

    @Override
    public IceInstruction visitCompareInstr(IceParser.CompareInstrContext ctx) {
        String resultReg = ctx.IDENTIFIER().getText();
        boolean isIcmp = ctx.getChild(2).getText().equals("icmp"); // Check if it's icmp or fcmp
        String opStr = ctx.cmpOp().getText().toUpperCase(); // e.g., EQ, NE, SLT
        IceType type = typeVisitor.visit(ctx.type());
        IceValue lhs = getValue(ctx.value(0));
        IceValue rhs = getValue(ctx.value(1));

        // Log.should(lhs.getType().equals(type) && rhs.getType().equals(type), "Compare operand type mismatch");
        // Log.should(isIcmp ? type.isIntegerType() || type.isPointerType() : type.isFloatType(), "Compare type mismatch (icmp/fcmp)");

        IceCmpInstruction cmpInst;
        if (isIcmp) {
            var cmpType = IceCmpInstruction.Icmp.Type.valueOf(opStr);
            cmpInst = new IceCmpInstruction.Icmp(block, getPureName(resultReg), cmpType, lhs, rhs);
        } else {
            var cmpType = IceCmpInstruction.Fcmp.Type.valueOf("O" + opStr); // Add O prefix for ordered float comparison
            cmpInst = new IceCmpInstruction.Fcmp(block, getPureName(resultReg), cmpType, lhs, rhs);
        }

        putValue(resultReg, cmpInst);
        return cmpInst;
    }

    @Override
    public IceInstruction visitConvertInstr(IceParser.ConvertInstrContext ctx) {
        String resultReg = ctx.IDENTIFIER().getText();
        IceValue value = getValue(ctx.value());
        IceType toType = typeVisitor.visit(ctx.type(1));

        IceConvertInstruction convertInst = new IceConvertInstruction(block, getPureName(resultReg), toType, value);
        putValue(resultReg, convertInst);
        return convertInst;
    }

    @Override
    public IceInstruction visitUnreachableInstr(IceParser.UnreachableInstrContext ctx) {
        return new IceUnreachableInstruction(block);
    }

    // Override default behavior for instruction node to delegate to specific visits
    @Override
    public IceInstruction visitInstruction(IceParser.InstructionContext ctx) {
        return visitChildren(ctx);
    }

    // Override default behavior for terminator instruction node
    @Override
    public IceInstruction visitTerminatorInstr(IceParser.TerminatorInstrContext ctx) {
        return visitChildren(ctx);
    }
}
