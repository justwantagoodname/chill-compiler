package top.voidc.frontend.translator;

import org.antlr.v4.runtime.ParserRuleContext;
import org.antlr.v4.runtime.tree.xpath.XPath;
import top.voidc.frontend.parser.SysyBaseVisitor;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceConvertInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.misc.Log;

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
            Log.should(innerIceBlock != null, "CFGEmitter visit blockItem should not return null");
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
        if (ctx.exp() != null) expEmitter.visit(ctx.exp());
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

        if (lValueType.isConst()) {
            throw new CompilationException("对常量 " + ctx.lVal().getText() + " 的修改", ctx, context);
        }

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
        final var ifName = currentFunction.generateLocalValueName();

        final var endBlock = new IceBlock(currentFunction, "if.end" + ifName);
        final var thenBlock = new IceBlock(currentFunction, "if.then" + ifName);
        final var thenEndBlock = ctx.thenStmt.accept(new CFGEmitter(context, thenBlock));
        if (!thenEndBlock.equals(currentFunction.getExitBlock())) {
            final var brInstr = new IceBranchInstruction(thenEndBlock, endBlock);
            thenEndBlock.addInstruction(brInstr);
        }

        IceBlock elseBlock = endBlock;
        if (hasElse) {
            elseBlock = new IceBlock(currentFunction, "if.else" + ifName);
            final var elseEndBlock = new CFGEmitter(context, elseBlock)
                    .visit(ctx.elseStmt);
            if (!elseEndBlock.equals(currentFunction.getExitBlock())) {
                final var brInstr = new IceBranchInstruction(elseEndBlock, endBlock);
                elseEndBlock.addInstruction(brInstr);
            }
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

        final var whileName = currentFunction.generateLocalValueName();
        final var condBlock = new IceBlock(currentFunction, "while.cond" + whileName);
        final var bodyBlock = new IceBlock(currentFunction, "while.body" + whileName);
        final var endBlock = new IceBlock(currentFunction, "while.end" + whileName);
        currentBlock.addInstruction(new IceBranchInstruction(currentBlock, condBlock));

        context.getLoopLabelStack().push(new IceContext.IceLoopLabel(condBlock, endBlock));

        context.getIfLabelStack().push(new IceContext.IceIfLabel(bodyBlock, endBlock));
        new CondEmitter(context, condBlock)
                .visitCond(ctx.cond());
        context.getIfLabelStack().pop();

        final var bodyEndBlock = ctx.stmt().accept(new CFGEmitter(context, bodyBlock));

        if (!bodyEndBlock.equals(currentFunction.getExitBlock())) {
            bodyEndBlock.addInstruction(new IceBranchInstruction(bodyEndBlock, condBlock));
        }

        context.getLoopLabelStack().pop();

        return endBlock;
    }

    private boolean isNotInLoop(ParserRuleContext ctx) {
        ParserRuleContext cur = ctx;
        while (cur.getParent() != null) {
            if (cur instanceof SysyParser.WhileStmtContext) {
                return false;
            }
            cur = cur.getParent();
        }
        return true;
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

    /**
     * 不改变控制流直接返回当前块
     * @param ctx the parse tree
     */
    @Override
    public IceBlock visitConstDecl(SysyParser.ConstDeclContext ctx) {
        ctx.accept(new ConstDeclEmitter(context));
        return currentBlock;
    }

    @Override
    public IceBlock visitVarDecl(SysyParser.VarDeclContext ctx) {
        ctx.accept(new VarDeclEmitter(context, currentBlock));
        return currentBlock;
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
