package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

/**
 * IceCopyInstruction 用于将一个值复制到另一个值
 * 此指令应该仅在SSA消除后出现，<b>这个指令破坏了SSA形式</b>
 */
public class IceCopyInstruction extends IceInstruction {

    public IceCopyInstruction(IceBlock parent, IceValue destination, IceValue source) {
        super(parent, IceType.VOID);
        this.addOperand(destination);
        this.addOperand(source);
    }

    public IceValue getSource() {
        return getOperand(1);
    }

    public IceValue getDestination() {
        return getOperand(0);
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("copy ")
                .append(getOperand(0).getReferenceName())
                .append(" to ")
                .append(getOperand(1).getReferenceName());
    }
}
