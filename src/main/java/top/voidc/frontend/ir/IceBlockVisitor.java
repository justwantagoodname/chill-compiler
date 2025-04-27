package top.voidc.frontend.ir;

import top.voidc.frontend.parser.IceBaseVisitor;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceInstruction;

import java.util.Map;

public class IceBlockVisitor extends IceBaseVisitor<IceBlock> {

    private final IceFunction function;
    private final Map<String, IceValue> environment;

    public IceBlockVisitor(IceFunction parentFunction, Map<String, IceValue> environment) {
        this.function = parentFunction;
        this.environment = environment;
    }
    @Override
    public IceBlock visitBasicBlock(IceParser.BasicBlockContext ctx) {
        if (ctx.terminatorInstr() == null) {
            throw new IllegalArgumentException("基本块必须以终止指令结尾");
        }
        
        // 1. 获取基本块名称(去掉%前缀)
        String blockName = ctx.IDENTIFIER().getText().substring(1);
        
        // 2. 创建基本块
        IceBlock block = new IceBlock(function, blockName);
        environment.put(blockName, block);
        
        // 3. 创建指令访问器
        InstructionVisitor instrVisitor = new InstructionVisitor(block, environment);
        
        // 4. 访问所有普通指令
        for (IceParser.InstructionContext instrCtx : ctx.instruction()) {
            IceInstruction instr = instrVisitor.visit(instrCtx);
            block.addInstruction(instr);
        }
        
        // 5. 访问终止指令
        IceInstruction termInstr = instrVisitor.visit(ctx.terminatorInstr());
        block.addInstruction(termInstr);
        
        return block;
    }
}
