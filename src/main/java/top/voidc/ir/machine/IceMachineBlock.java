package top.voidc.ir.machine;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;

import java.util.ArrayList;
import java.util.List;

public class IceMachineBlock extends IceBlock implements IceMachineValue {
    public IceMachineBlock(IceFunction parentFunction, String name) {
        super(parentFunction, name);
    }

    @Override
    public List<IceBlock> getSuccessors() {
        var result = new ArrayList<IceBlock>();
        this.stream().filter(IceInstruction::isTerminal).forEach(terminalInstr ->
                terminalInstr.getOperands().stream()
                        .filter(operand -> operand instanceof IceBlock).map(operand -> (IceBlock) operand)
                        .forEach(result::add));
        return result;
    }

    @Override
    public String getReferenceName(boolean withType) {
        return getName();
    }
}
