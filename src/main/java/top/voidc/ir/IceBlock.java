package top.voidc.ir;

import top.voidc.ir.instruction.IceInstruction;
import top.voidc.ir.type.IceType;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class IceBlock extends IceValue {
    private final Set<IceBlock> predecessors;
    private final Set<IceBlock> successors;

    private final List<IceInstruction> instructions;

    public IceBlock(String name) {
        super(name, IceType.VOID);
        this.predecessors = new HashSet<>();
        this.successors = new HashSet<>();
        this.instructions = new ArrayList<>();
    }

    public void addPredecessor(IceBlock block) {
        predecessors.add(block);
    }

    public void addSuccessor(IceBlock block) {
        successors.add(block);
    }

    public void addInstruction(IceInstruction instruction) {
        instructions.add(instruction);
    }

    public void addInstructions(List<IceInstruction> instructions) {
        this.instructions.addAll(instructions);
    }

}
