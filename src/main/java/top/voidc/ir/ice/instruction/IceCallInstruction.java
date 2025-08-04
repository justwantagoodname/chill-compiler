package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceType;

import java.util.List;

public class IceCallInstruction extends IceInstruction {

    public IceCallInstruction(IceBlock block, IceFunction target, List<IceValue> args) {
        super(block, target.getReturnType());
        addOperand(target);
        args.forEach(this::addOperand);
    }

    public IceCallInstruction(IceBlock block, String name, IceFunction target, List<IceValue> args) {
        super(block, name, target.getReturnType());
        addOperand(target);
        args.forEach(this::addOperand);
    }

    public IceFunction getTarget() {
        return (IceFunction) getOperand(0);
    }

    /**
     * 获取调用指令的参数列表
     * @return 参数列表
     */
    public List<IceValue> getArguments() {
        return getOperands().subList(1, getOperands().size());
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        if (!getType().isVoid()) {
            builder.append("%").append(getName()).append(" = ");
        }
        builder.append("call ").append(getTarget().getReturnType());

        if (getTarget() instanceof IceExternFunction externFunction && externFunction.isVArgs()) {
            final var funcTypeSignature = new java.util.ArrayList<>(getTarget().getParameterTypes()
                    .stream().map(IceType::toString).toList());
            funcTypeSignature.add("...");
            builder.append(" (")
                    .append(String.join(", ", funcTypeSignature))
                    .append(")");
        }

        builder.append(" @").append(getTarget().getName()).append("(");
        
        var args = getArguments();
        for (int i = 0; i < args.size(); i++) {
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(args.get(i).getReferenceName());
        }
        builder.append(")");
    }
}
