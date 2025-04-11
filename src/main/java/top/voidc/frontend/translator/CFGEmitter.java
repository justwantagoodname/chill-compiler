package top.voidc.frontend.translator;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.xpath.XPath;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceConvertInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.type.IcePtrType;

/**
 * 访问函数的顶级块中的 item 来构建构建控制流图，每个class都对应一个基本块。
 * 之所以要单独开一个类，是因为 AntLR 的 visitor 不能重载函数的参数
 */
public class CFGEmitter extends SysyBaseVisitor<IceBlock> {
    private final IceBlock currentBlock; // 当前控制流所处的基本块
    private final IceContext context;
    private final IceFunction currentFunction;

    public CFGEmitter(IceContext context, IceBlock block) {
        this.context = context;
        this.currentBlock = block;
        this.currentFunction = context.getCurrentFunction();
    }

    /**
     * 解析 Block 元素，注意顶层 Block 的作用域在函数那里处理过了，
     * @param ctx the parse tree
     * @return 返回匿名作用域的下一个基本块
     */
    @Override
    public IceBlock visitBlock(SysyParser.BlockContext ctx) {
        final var isAnonBlock = !(ctx.parent instanceof SysyParser.FuncDefContext);
        if (isAnonBlock) {
            context.getSymbolTable().createScope("::anonymous");
        }

        var innerIceBlock = this.currentBlock;

        for (final var blockItem: ctx.blockItem()) {
            final var cfgEmitter = new CFGEmitter(context, innerIceBlock);
            innerIceBlock = cfgEmitter.visit(blockItem);
            if (innerIceBlock.equals(currentFunction.getExitBlock())) {
                // 如果当前块是 exitBlock，说明遇到了 return/break/continue 语句后面的语句均为死代码跳过
                break;
            }
        }

        if (isAnonBlock) {
            context.getSymbolTable().exitScope();
        }

        return innerIceBlock;
    }

    // 解析 Stmt 元素

    /**
     * 解析表达式语句
     * @param ctx the parse tree
     * @return 不改变控制流直接返回
     */
    @Override
    public IceBlock visitExprStmt(SysyParser.ExprStmtContext ctx) {
        final var expEmitter = new ExpEmitter(context, currentBlock);
        expEmitter.visit(ctx.exp());
        return currentBlock;
    }

    /**
     *
     * @param ctx the parse tree
     * @return 不改变控制流直接返回
     */
    @Override
    public IceBlock visitAssignStmt(SysyParser.AssignStmtContext ctx) {
        final var expEmitter = new ExpEmitter(context, currentBlock);

        var rValue = expEmitter.visit(ctx.exp());
        final var lValue = expEmitter.visitLVal(ctx.lVal());

        final IcePtrType<?> lValueType = (IcePtrType<?>) lValue.getType();

        if (!rValue.getType().equals(lValueType.getPointTo())) {
            if (!rValue.getType().isConvertibleTo(lValueType.getPointTo())) {
                throw new CompilationException("类型不匹配", ctx, context);
            } else {
                final var convertInstruction = new IceConvertInstruction(currentBlock, lValueType.getPointTo(), rValue);
                currentBlock.addInstruction(convertInstruction);
                rValue = convertInstruction;
            }
        }

        final var instr = new IceStoreInstruction(currentBlock, lValue, rValue);
        currentBlock.addInstruction(instr);

        return currentBlock;
    }

    /**
     * 解析 If 语句
     * @param ctx the parse tree
     * @return 返回 if-else 结束后的基本块
     */
    @Override
    public IceBlock visitIfStmt(SysyParser.IfStmtContext ctx) {
        final var hasElse = ctx.elseStmt != null;

        final var endBlock = new IceBlock(currentFunction);

        var thenBlock = new IceBlock(currentFunction);
        thenBlock = new CFGEmitter(context, thenBlock)
                    .visit(ctx.thenStmt);
        final var brInstr = new IceBranchInstruction(thenBlock, endBlock);
        thenBlock.addInstruction(brInstr);

        IceBlock elseBlock = endBlock;
        if (hasElse) {
            elseBlock = new IceBlock(currentFunction);
            elseBlock = new CFGEmitter(context, elseBlock)
                    .visit(ctx.elseStmt);
            final var elseBrInstr = new IceBranchInstruction(elseBlock, endBlock);
            elseBlock.addInstruction(elseBrInstr);
        }

        context.getIfLabelStack().push(
                new IceContext.IceIfLabel(thenBlock, hasElse ? elseBlock : endBlock));
        final var condEmitter = new CondEmitter(context, currentBlock);
        condEmitter.visitCond(ctx.cond());
        context.getIfLabelStack().pop();

        return endBlock;
    }

    @Override
    public IceBlock visitWhileStmt(SysyParser.WhileStmtContext ctx) {

        final var condBlock = new IceBlock(currentFunction);
        final var bodyBlock = new IceBlock(currentFunction);
        final var endBlock = new IceBlock(currentFunction);
        currentBlock.addInstruction(new IceBranchInstruction(currentBlock, condBlock));

        context.getLoopLabelStack().push(new IceContext.IceLoopLabel(condBlock, endBlock));

        context.getIfLabelStack().push(new IceContext.IceIfLabel(bodyBlock, endBlock));
        new CondEmitter(context, condBlock)
                .visitCond(ctx.cond());
        context.getIfLabelStack().pop();

        final var bodyEmitter = new CFGEmitter(context, bodyBlock);
        bodyEmitter.visit(ctx.stmt());
        bodyBlock.addInstruction(new IceBranchInstruction(bodyBlock, condBlock));

        context.getLoopLabelStack().pop();

        return endBlock;
    }

    private boolean isNotInLoop(ParserRuleContext ctx) {
        return XPath.findAll(ctx, "ancestor::whileStmt", context.getParser()).isEmpty();
    }

    /**
     * 解析 Return 语句，终止当前基本块
     * @param ctx the parse tree
     * @return 中止当前基本块返回函数终止块
     */
    @Override
    public IceBlock visitReturnStmt(SysyParser.ReturnStmtContext ctx) {
        final IceRetInstruction retInstruction;
        if (ctx.exp() != null) {
            final var expEmitter = new ExpEmitter(context, currentBlock);
            final var retValue = expEmitter.visit(ctx.exp());
            retInstruction = new IceRetInstruction(currentBlock, retValue);
        } else {
            retInstruction = new IceRetInstruction(currentBlock);
        }
        currentBlock.addInstruction(retInstruction);

        return currentFunction.getExitBlock();
    }

    @Override
    public IceBlock visitBreakStmt(SysyParser.BreakStmtContext ctx) {
        if (isNotInLoop(ctx)) {
            throw new CompilationException("break 语句只能在循环中使用", ctx, context);
        }

        final var loopLabel = context.getLoopLabelStack().peek();

        currentBlock.addInstruction(new IceBranchInstruction(currentBlock, loopLabel.endLabel()));

        // 在这里直接返回 exitBlock 这样上一级遍历器就不会继续遍历当前块了
        return context.getCurrentFunction().getExitBlock();
    }

    @Override
    public IceBlock visitContinueStmt(SysyParser.ContinueStmtContext ctx) {
        if (isNotInLoop(ctx)) {
            throw new CompilationException("continue 语句只能在循环中使用", ctx, context);
        }

        final var loopLabel = context.getLoopLabelStack().peek();

        currentBlock.addInstruction(new IceBranchInstruction(currentBlock, loopLabel.condLabel()));

        // 在这里直接返回 exitBlock 这样上一级遍历器就不会继续遍历当前块了
        return context.getCurrentFunction().getExitBlock();
    }

    // 解析 Decl 元素
    //    private IceValue visitVarArrayDef(SysyParser.VarDefContext ctx) {
//        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
//        var varType = IceType.fromSysyLiteral(typeLiteral);
//        final var name = ctx.Ident().getText();
//        final var arraySize = new ArrayList<Integer>();
//        for (final var arraySizeItem : ctx.constExp()) {
//            final var result = arraySizeItem.accept(new ConstExpEvaluator());
//            Log.should(result instanceof IceConstantInt, "Array size must be constant");
//            arraySize.add((int) ((IceConstantInt) result).getValue());
//        }
//        varType = IceArrayType.buildNestedArrayType(arraySize, varType);
//        final var alloca = new IceAllocaInstruction(functionEntity.generateLocalValueName(), varType);
//        functionEntity.insertInstructionFirst(alloca);
//        SymbolTable.current().put(name, alloca);
//        return alloca;
//    }
//
//    @Override
//    public IceValue visitVarDef(SysyParser.VarDefContext ctx) {
//        final var typeLiteral = ((SysyParser.VarDeclContext) ctx.parent).primitiveType().getText();
//        final var varType = IceType.fromSysyLiteral(typeLiteral);
//        final var name = ctx.Ident().getText();
//
//        if (!ctx.constExp().isEmpty()) {
//            return visitVarArrayDef(ctx);
//        }
//
//        if (ctx.initVal() != null) {
//            final var initVal = (IceBlock) ctx.initVal().accept(new ConstExpEvaluator());
//            return null;
//        }
//
//
//        // For non-array type, generate an IceAllocaInstruction
//        final var alloca = new IceAllocaInstruction(functionEntity.generateLocalValueName(), varType);
//        functionEntity.insertInstructionFirst(alloca);
//        SymbolTable.current().put(name, alloca);
//        return alloca;
//    }
}
