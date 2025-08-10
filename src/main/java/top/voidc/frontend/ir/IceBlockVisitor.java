package top.voidc.frontend.ir;

import top.voidc.frontend.parser.IceBaseVisitor;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class IceBlockVisitor extends IceBaseVisitor<IceBlock> {

    private final IceFunction function;
    private final Map<String, IceValue> environment;
    private IceBlock currentBlock;
    private final List<Runnable> postProcessActions = new ArrayList<>();

    public List<Runnable> getPostProcessActions() {
        return postProcessActions;
    }

    public IceBlockVisitor(IceFunction parentFunction, Map<String, IceValue> environment) {
        this.function = parentFunction;
        this.environment = environment;
        this.currentBlock = null;
    }

    public IceBlockVisitor(IceFunction parentFunction, Map<String, IceValue> environment, IceBlock block) {
        this.function = parentFunction;
        this.environment = environment;
        this.currentBlock = block;
    }
    @Override
    public IceBlock visitBasicBlock(IceParser.BasicBlockContext ctx) {
        if (ctx.terminatorInstr() == null) {
            throw new IllegalArgumentException("基本块必须以终止指令结尾");
        }
        
        // 如果没有预创建的block，则创建新的
        if (currentBlock == null) {
            var blockName = ctx.NAME().getText();
            currentBlock = new IceBlock(function, blockName);
            environment.put(blockName, currentBlock);
        }
        
        // 访问所有普通指令
        for (var instrCtx : ctx.instruction()) {
            var instrVisitor = new InstructionVisitor(currentBlock, environment);
            var instr = instrVisitor.visit(instrCtx);
            currentBlock.addInstruction(instr);
            if (instrVisitor.getPostAction() != null) postProcessActions.add(instrVisitor.getPostAction());
        }
        
        // 访问终止指令
        var instrVisitor = new InstructionVisitor(currentBlock, environment);
        var termInstr = instrVisitor.visit(ctx.terminatorInstr());
        if (instrVisitor.getPostAction() != null) postProcessActions.add(instrVisitor.getPostAction());
        currentBlock.addInstruction(termInstr);
        
        return currentBlock;
    }
}
