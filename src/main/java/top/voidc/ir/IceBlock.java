package top.voidc.ir;

import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceType;

import java.util.ArrayList;
import java.util.List;

public class IceBlock extends IceUser {
    private final List<IceInstruction> instructions;
    private final IceFunction function; // 所属函数

    @Override
    public String toString() {
        final StringBuffer sb = new StringBuffer(this.getName() + ":\n");
        instructions.forEach(i -> sb.append("\t").append(i.toString()).append("\n"));
        return sb.toString();
    }

    public IceBlock(IceFunction parentFunction, String name) {
        super(name, IceType.VOID);
        this.function = parentFunction;
        this.instructions = new ArrayList<>();
    }

    public IceFunction getFunction() {
        return function;
    }

    public void addInstruction(IceInstruction instruction) {
        instructions.add(instruction);
    }

    public void addInstructionsAtFront(IceInstruction instructions) {
        this.instructions.add(0, instructions);
    }

    public List<IceInstruction> getInstructions() {
        return instructions;
    }

    public Iterable<IceInstruction> instructions() {
        return instructions;
    }

    public Iterable<IceBlock> successors() {
        return (Iterable<IceBlock>) this.getOperands();
    }

    public Iterable<IceBlock> predecessors() {
        return (Iterable<IceBlock>) this.getUsers();
    }
}
