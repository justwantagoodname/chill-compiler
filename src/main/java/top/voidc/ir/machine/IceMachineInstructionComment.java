package top.voidc.ir.machine;

// 用于保存汇编中的注释
public class IceMachineInstructionComment extends IceMachineInstruction {
    public IceMachineInstructionComment(String renderTemplate) {
        super(renderTemplate);
    }

    @Override
    public IceMachineInstruction clone() {
        return new IceMachineInstructionComment(this.renderTemplate);
    }
}
