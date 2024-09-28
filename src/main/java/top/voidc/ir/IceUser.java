package top.voidc.ir;

import top.voidc.ir.ice.type.IceType;

import java.util.ArrayList;
import java.util.List;

public class IceUser extends IceValue {
    private List<IceValue> operands;

    public IceUser() {
        super();
        this.operands = new ArrayList<>();
    }

    public IceUser(String name, IceType type) {
        super(name, type);
        this.operands = new ArrayList<>();
    }

    public void addOperand(IceValue operand) {
        operand.addUse(this);
        operands.add(operand);
    }

    public IceValue getOperand(int i) {
        return operands.get(i);
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
