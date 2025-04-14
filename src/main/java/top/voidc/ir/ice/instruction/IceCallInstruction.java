package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;

import java.util.List;

public class IceCallInstruction extends IceInstruction {

    public IceCallInstruction(IceBlock block, IceFunction target, List<IceValue> args) {
        super(block, target.getReturnType());
        setInstructionType(InstructionType.CALL);
        addOperand(target);
        args.forEach(this::addOperand);
    }

    public IceFunction getTarget() {
        return (IceFunction) getOperand(0);
    }

    public List<IceValue> getArguments() {
        return getOperandsList().subList(1, getOperandsList().size());
    }

    @Override
    public String toString() {
        final var args = getArguments();
        return "call " + getTarget().getReturnType() + " " + getTarget().getReferenceName()
                + "(" + String.join(",", args.stream().map(IceValue::toString).toList()) +")";
    }
}
