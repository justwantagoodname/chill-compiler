package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

/**
 * 条件分支指令
 * @apiNote 会自动添加父块的后继块
 */
public class IceBranchInstruction extends IceInstruction {

    private final boolean isConditional;

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("br ");
        if (isConditional) {
            builder.append(getCondition().getReferenceName())
                    .append(", ").append(getTrueBlock().getReferenceName())
                    .append(", ").append(getFalseBlock().getReferenceName());
        } else {
            builder.append(getTargetBlock().getReferenceName());
        }
    }

    public IceBranchInstruction(IceBlock parent, IceValue condition, IceBlock trueBlock, IceBlock falseBlock) {
        super(parent, IceType.VOID);
        Log.should(condition.getType().isBoolean(), "Condition must be boolean");
        this.isConditional = true;
        this.addOperand(condition);
        this.addOperand(trueBlock);
        this.addOperand(falseBlock);
    }

    public IceBranchInstruction(IceBlock parent, IceBlock targetBlock) {
        super(parent, IceType.VOID);
        this.isConditional = false;
        this.addOperand(targetBlock);
    }

    public boolean isConditional() {
        return isConditional;
    }

    public IceValue getCondition() {
        Log.should(isConditional(), "Must called on an conditional branch");
        return getOperand(0);
    }

    public IceBlock getTrueBlock() {
        Log.should(isConditional(), "Must called on an conditional branch");
        return (IceBlock) getOperand(1);
    }

    public IceBlock getFalseBlock() {
        Log.should(isConditional(), "Must called on an conditional branch");
        return (IceBlock) getOperand(2);
    }

    public IceBlock getTargetBlock() {
        Log.should(!isConditional(), "Must called on an conditional branch");
        return (IceBlock) getOperand(0);
    }

    @Override
    public boolean isTerminal() {
        return true;
    }

    @Override
    public void destroy() {
        for (var operand : getOperands()) {
            if (operand instanceof IceBlock block) {
                block.safeForEach(instr -> {
                    if (instr instanceof IcePHINode phi) {
                        phi.removeValueByBranch(this.getParent()); // 从 phi 节点中删除当前分支
                    }
                });
            }
        }
        super.destroy();
    }
}
