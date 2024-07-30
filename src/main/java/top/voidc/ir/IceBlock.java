package top.voidc.ir;

import top.voidc.ir.inst.IceInstruction;
import top.voidc.ir.type.IceType;

import java.util.ArrayList;
import java.util.List;

public class IceBlock extends IceValue {
    private final List<IceInstruction> instructions = new ArrayList<>();
    private final List<IceBlock> predecessors = new ArrayList<>();
    private final List<IceBlock> successors = new ArrayList<>();
    private final IceFunction parent;

    public IceBlock(String name, IceFunction parent) {
        super(name, IceType.VOID);
        this.parent = parent;
    }

    public void addInstruction(IceInstruction instruction) {
        instructions.add(instruction);
    }

    public void removeInstruction(IceInstruction instruction) {
        instructions.remove(instruction);
    }

    public List<IceInstruction> getInstructions() {
        return instructions;
    }

    public void addPredecessor(IceBlock block) {
        predecessors.add(block);
    }

    public void removePredecessor(IceBlock block) {
        predecessors.remove(block);
    }

    public List<IceBlock> getPredecessors() {
        return predecessors;
    }

    public void addSuccessor(IceBlock block) {
        successors.add(block);
    }

    public void removeSuccessor(IceBlock block) {
        successors.remove(block);
    }

    public List<IceBlock> getSuccessors() {
        return successors;
    }

    public IceInstruction getEntry() {
        return instructions.get(0);
    }

    public IceInstruction getExit() {
        return instructions.get(instructions.size() - 1);
    }

    public IceFunction getFunction() {
        return parent;
    }
}