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

    public IceBranchInstruction(IceBlock parent, IceValue condition, IceBlock trueBlock, IceBlock falseBlock) {
        super(parent, IceType.VOID);
        setInstructionType(InstructionType.BRANCH);
        Log.should(condition.getType().isBoolean(), "Condition must be boolean");
        this.isConditional = true;
        this.addOperand(condition);
        this.addOperand(trueBlock);
        this.addOperand(falseBlock);
        this.getParent().addSuccessor(trueBlock);
        this.getParent().addSuccessor(falseBlock);
    }

    public IceBranchInstruction(IceBlock parent, IceBlock targetBlock) {
        super(parent, IceType.VOID);
        setInstructionType(InstructionType.BRANCH);
        this.isConditional = false;
        this.addOperand(targetBlock);
        this.getParent().addSuccessor(targetBlock);
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
}
