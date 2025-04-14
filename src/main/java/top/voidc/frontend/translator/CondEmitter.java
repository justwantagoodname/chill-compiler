package top.voidc.frontend.translator;

import org.antlr.v4.runtime.ParserRuleContext;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.exception.CompilationException;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceCmpInstruction;
import top.voidc.ir.ice.instruction.IceFcmpInstruction;
import top.voidc.ir.ice.instruction.IceIcmpInstruction;

/**
 * 翻译条件语句为 IceIR 因为对于 && 和 || 需要短路求值改变控制流
 * 这里返回的 IceValue 分别是
 */
public class CondEmitter extends ExpEmitter {

    public CondEmitter(IceContext context, IceBlock block) {
        super(context, block);
    }

    /**
     * 处理返回值不是 boolean 的情况将其转换为 boolean，并且直接生成转跳指令
     */
    private void handleRawValue(ParserRuleContext ctx, IceValue value) {
        if (value != null) {
            if (!value.getType().isBoolean()) {
                // 内部不是布尔值，添加 CMP 指令
                final var cmpInstr = switch (value.getType().getTypeEnum()) {
                    case I32 -> new IceIcmpInstruction(block, IceCmpInstruction.CmpType.NE, value,
                            IceConstantData.create(0));
                    case F32 -> new IceFcmpInstruction(block, IceCmpInstruction.CmpType.NE, value,
                            IceConstantData.create(0F));
                    default -> throw new CompilationException(
                            value.getType().toString() + "不能转换为布尔值", ctx, context);
                };
                block.addInstruction(cmpInstr);
                value = cmpInstr;
            }

            // 添加转跳指令
            final var ifBlocks = context.getIfLabelStack().peek();
            final var brInstr = new IceBranchInstruction(block, value, ifBlocks.trueLabel(), ifBlocks.falseLabel());
            block.addInstruction(brInstr);
        }
    }

    /**
     * 处理 && ||
     * @return 永远为 null 仅生成控制流转跳
     */
    @Override
    protected IceValue visitLogicalExp(SysyParser.ExpContext ctx) {
        final var currentLabel = context.getIfLabelStack().peek();
        final var trueLabel = currentLabel.trueLabel();
        final var falseLabel = currentLabel.falseLabel();
        final var midLabel = new IceBlock(context.getCurrentFunction());

        switch (ctx.logicOp.getText()) {
            case "&&" -> {
                // 处理逻辑与
                // 逻辑是如果左边为假则直接跳转到 falseLabel，否则就跳转到 midLabel
                context.getIfLabelStack().push(new IceContext.IceIfLabel(midLabel, falseLabel));
            }
            case "||" -> {
                // 处理逻辑或
                // 逻辑是如果左边为真则直接跳转到 trueLabel，否则就跳转到 midLabel
                context.getIfLabelStack().push(new IceContext.IceIfLabel(trueLabel, midLabel));
            }
            default -> throw new CompilationException("不支持的逻辑操作符", ctx, context);
        }

        this.handleRawValue(ctx, this.visitExp(ctx.exp(0)));

        context.getIfLabelStack().pop();

        // 继续访问右边，但是已经在 midLabel 的基本块中
        final var condEmitter = new CondEmitter(context, midLabel);
        condEmitter.handleRawValue(ctx, condEmitter.visitExp(ctx.exp(1)));

        return null;
    }

    /**
     * 处理逻辑非
     * @return 永远为 null 仅生成控制流转跳
     */
    @Override
    protected IceValue visitLogicNotExp(SysyParser.ExpContext ctx) {
        final var currentLabel = context.getIfLabelStack().peek();
        // 仅仅交换 true 和 false 的标签
        context.getIfLabelStack().push(new IceContext.IceIfLabel(
                currentLabel.falseLabel(), currentLabel.trueLabel()));

        this.handleRawValue(ctx, this.visitExp(ctx.exp(0)));

        context.getIfLabelStack().pop();
        return null;
    }

    /**
     * 解析条件语句的入口
     * @param ctx the parse tree
     * @return 永远为 null 仅生成控制流转跳
     */
    @Override
    public IceValue visitCond(SysyParser.CondContext ctx) {
        this.handleRawValue(ctx, this.visitExp(ctx.exp()));
        return null;
    }
}
