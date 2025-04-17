package top.voidc.ir;

import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.StreamTools;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.StreamSupport;

public class IceBlock extends IceUser {
    private final List<IceInstruction> instructions;
    private final IceFunction function; // 所属函数

    public IceBlock(IceFunction parentFunction, String name) {
        super(name, IceType.VOID);
        this.function = parentFunction;
        this.instructions = new ArrayList<>();
    }

    public IceBlock(IceFunction parentFunction) {
        super("L" + parentFunction.generateLocalValueName(), IceType.VOID);
        this.function = parentFunction;
        this.instructions = new ArrayList<>();
    }

    public IceFunction getFunction() {
        return function;
    }

    public void addInstruction(IceInstruction instruction) {
        instructions.add(instruction);
    }

    public void removeInstruction(IceInstruction instruction) {
        instructions.remove(instruction);
    }

    public void addInstructionAtFront(IceInstruction instruction) {
        this.instructions.add(0, instruction);
    }

    public List<IceInstruction> getInstructions() {
        return instructions;
    }

    public List<IceInstruction> instructions() {
        return instructions;
    }

    public Iterable<IceBlock> successors() {
        return (Iterable<IceBlock>) this.getOperands();
    }

    public List<IceBlock> getSuccessors() {
        return StreamTools.toList(successors());
    }

    public Iterable<IceBlock> predecessors() {
        return getPredecessors();
    }

    public List<IceBlock> getPredecessors() {
        return StreamSupport.stream(getUsers().spliterator(), false)
                .filter(iceUser -> iceUser instanceof IceBlock)
                .map(iceUser -> (IceBlock) iceUser).toList();
    }

    public void addSuccessor(IceBlock block) {
        this.addOperand(block);
    }

    public void removeSuccessor(IceBlock block) {
        this.removeOperand(block);
    }

    @Override
    public String getReferenceName() {
        return "label %" + getName();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append(this.getName()).append(":\n");
        instructions
                .forEach(instr -> {
                    builder.append("\t");
                    instr.getTextIR(builder);
                    builder.append("\n");
                });
    }
}
