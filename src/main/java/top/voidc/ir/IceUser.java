package top.voidc.ir;

import java.util.ArrayList;
import java.util.List;

public class IceUser extends IceValue {
    private List<IceValue> operands;

    public IceUser() {
        super();
        this.operands = new ArrayList<>();
    }

    public IceUser(String name) {
        this.operands = new ArrayList<>();
    }

    public void addOperand(IceValue operand) {
        operand.addUse(this);
        operands.add(operand);
    }

    public List<IceValue> getOperands() {
        return operands;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IceUser with operands: ");
        for (IceValue operand : operands) {
            sb.append(operand).append(" ");
        }
        return sb.toString();
    }
}
