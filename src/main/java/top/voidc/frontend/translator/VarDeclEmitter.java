package top.voidc.frontend.translator;

import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.ArrayList;
import java.util.List;

/**
 * 处理函数 Block 中的变量声明
 */
public class VarDeclEmitter extends ConstDeclEmitter {
    private static final int COPY_THRESHOLD = 32; // 使用 memcpy 的阈值以总元素个数

    private final IceBlock currentBlock;
    private final IceFunction currentFunction;

    public VarDeclEmitter(IceContext context, IceBlock currentBlock) {
        super(context);
        this.currentBlock = currentBlock;
        this.currentFunction = context.getCurrentFunction();
    }

    /**
     * 常量直接变字面量
     */

    @Override
    protected IceValue visitArrayInitValExp(SysyParser.ExpContext ctx, IceType targetType) {
        // 内部是一个表达式，这个表达式不用是一个常量表达式
        var initValue = ctx.accept(new ExpEmitter(context, currentBlock));
        if (!initValue.getType().equals(targetType)) {
            if (!initValue.getType().isConvertibleTo(targetType)) {
                throw new CompilationException("Cannot convert " + initValue.getType() + " to " + targetType,
                        ctx, context);
            }
            final var convInstr = new IceConvertInstruction(
                    currentFunction.getEntryBlock(),
                    targetType, initValue);
            currentBlock.addInstruction(convInstr);
            initValue = convInstr;
        }
        return initValue;
    }

    @Override
    protected void visitSingleVarDecl(SysyParser.VarDefContext ctx) {
        final var varType = getVarType(ctx);
        final var name = ctx.Ident().getText();

        final var varStackPtr = new IceAllocaInstruction(
                currentFunction.getEntryBlock(), varType);

        // 首先在栈上分配空间
        currentFunction.getEntryBlock().addInstructionAtFront(varStackPtr);

        if (ctx.initVal() != null) {
            var initValue = ctx.initVal().exp().accept(new ExpEmitter(context, currentBlock));
            if (!initValue.getType().equals(varType)) {
                if (!initValue.getType().isConvertibleTo(varType)) {
                    throw new CompilationException("Cannot convert " + initValue.getType() + " to " + varType,
                            ctx, context);
                }
                initValue = new IceConvertInstruction(
                        currentFunction.getEntryBlock(),
                        varType, initValue);
            }
            final var store = new IceStoreInstruction(currentBlock, varStackPtr, initValue);
            currentBlock.addInstruction(store);
        }
        context.getSymbolTable().put(name, varStackPtr);
    }

    @Override
    protected void visitConstArrayDef(SysyParser.ConstDefContext ctx) {
        final var elementType = getConstType(ctx);
        final var name = ctx.Ident().getText();
        final var arraySize = getConstDeclSize(ctx);
        final var arrayType = IceArrayType.buildNestedArrayType(arraySize, elementType);


        // 在栈上分配空间
        final var varStackPtr = new IceAllocaInstruction(
                currentFunction.getEntryBlock(), arrayType);
        ((IcePtrType<?>)varStackPtr.getType()).setConst(true);

        currentFunction.getEntryBlock().addInstructionAtFront(varStackPtr);

        handleArrayInit(varStackPtr, arraySize, ctx.initVal());

        context.getSymbolTable().put(name, varStackPtr);
    }

    @Override
    protected void visitVarArrayDecl(SysyParser.VarDefContext ctx) {
        final var elementType = getVarType(ctx);
        final var name = ctx.Ident().getText();
        final var arraySize = getArrayDeclSize(ctx);
        final var arrayType = IceArrayType.buildNestedArrayType(arraySize, elementType);

        // 在栈上分配空间
        final var varStackPtr = new IceAllocaInstruction(
                currentFunction.getEntryBlock(), arrayType);
        currentFunction.getEntryBlock().addInstructionAtFront(varStackPtr);

        if (ctx.initVal() != null) handleArrayInit(varStackPtr, arraySize, ctx.initVal());

        context.getSymbolTable().put(name, varStackPtr);
    }

    /**
     * 处理数组初始化，常量数组和变量是一样的
     */
    protected void handleArrayInit(IceValue arrayPtr, List<Integer> arraySize, SysyParser.InitValContext ctx) {
        final var arrayShapeType = (IceArrayType) ((IcePtrType<?>) arrayPtr.getType()).getPointTo();
        final var initArray = new IceConstantArray(arrayShapeType, new ArrayList<>());
        // compute dimension size
        arraySize.add(1);

        visited.clear();
        fillArray(initArray, ctx, arraySize, 0);
        visited.clear();

        final var arrayByteSize = arrayShapeType.getTotalSize() * arrayShapeType.getElementType().getByteSize();

        final var nonZeroElements = initArray.getNonZeroElements();
        final var isConstInit = nonZeroElements.stream().allMatch(elem -> elem.value() instanceof IceConstantData);

        // 这个只是一个容器，真正初始化由 memcpy 和 gep 完成
        if (nonZeroElements.isEmpty()) {
            final var instr = new IceIntrinsicInstruction(
                    currentBlock,
                    IceType.VOID,
                    IceIntrinsicInstruction.MEMSET,
                    List.of(arrayPtr,
                            IceConstantData.create((byte) 0),
                            IceConstantData.create(arrayByteSize),
                            IceConstantData.create(false)
                    )
            );
            currentBlock.addInstruction(instr);
        } else if (isConstInit && arrayByteSize <= COPY_THRESHOLD) {
            // 全常量数组 使用 memcpy
            final var globalCopyArray = new IceGlobalVariable(
                    "__const." + currentFunction.getName() + "." + arrayPtr.getName(),
                    arrayShapeType, initArray);
            globalCopyArray.setPrivate(true);
            globalCopyArray.setUnnamedAddr(true);

            context.getCurrentIR().addGlobalDecl(globalCopyArray);

            final var memcpyInstr = new IceIntrinsicInstruction(currentBlock, IceType.VOID, IceIntrinsicInstruction.MEMCPY,
                                            List.of(
                                                    arrayPtr, // dst
                                                    globalCopyArray, // src
                                                    IceConstantData.create(arrayByteSize),
                                                    IceConstantData.create(false)
                                            ));
            currentBlock.addInstruction(memcpyInstr);

        } else {
            // memset + GEP + store
            final var instr = new IceIntrinsicInstruction(
                    currentBlock,
                    IceType.VOID,
                    IceIntrinsicInstruction.MEMSET,
                    List.of(arrayPtr,
                            IceConstantData.create((byte) 0),
                            IceConstantData.create(arrayByteSize),
                            IceConstantData.create(false)
                    )
            );
            currentBlock.addInstruction(instr);

            nonZeroElements.forEach(
                    elementRecord -> {
                        final var iceValueIndices = new ArrayList<>(elementRecord.position().stream().map(
                                index -> (IceValue) IceConstantData.create(index)
                        ).toList());
                        iceValueIndices.add(0, IceConstantInt.create(0));

                        final var gep = new IceGEPInstruction(
                                currentBlock,
                                arrayPtr,
                                iceValueIndices
                        );

                        currentBlock.addInstruction(gep);

                        final var store = new IceStoreInstruction(
                                currentBlock,
                                gep,
                                elementRecord.value()
                        );
                        currentBlock.addInstruction(store);
                    }
            );
        }

    }
}
