package top.voidc.ir;

import top.voidc.frontend.ir.IceBlockVisitor;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceBranchInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IceRetInstruction;
import top.voidc.ir.ice.instruction.IceUnreachableInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.*;
import java.util.stream.Collectors;

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

    /**
     * 移除当前基本块index指令*后*中所有的指令，前驱和后继关系由destroy中的相关代码维护
     * @param index 指令索引 index不会删除
     */
    private void removeAfterInstruction(int index) {
        Log.w("在基本块中间插入了终止指令，确定这是想要的吗？");
        List.copyOf(this.instructions.subList(index + 1, instructions.size())).forEach(IceInstruction::destroy);
    }

    /**
     * 在基本块最后插入指令，会自动维护前驱后继关系
     * @param instruction 指令
     */
    public void addInstruction(IceInstruction instruction) {
        instructions.add(instruction);
        if (instruction.isTerminal()) removeAfterInstruction(instructions.size() - 1);
    }

    /**
     * 在基本块最前面插入指令，会自动维护前驱后继关系
     * @param instruction 指令
     */
    public void addInstructionAtFront(IceInstruction instruction) {
        this.instructions.addFirst(instruction);
        if (instruction.isTerminal()) {
            Log.w("在基本块最前面插入了终止指令，确定这是想要的吗？");
            removeAfterInstruction(0);
        }
    }

    public void addInstructionAfter(IceInstruction instruction, IceInstruction after) {
        int index = instructions.indexOf(after);
        if (index == -1) {
            throw new IllegalArgumentException("指令不在基本块中");
        }
        instructions.add(index + 1, instruction);
        if (instruction.isTerminal()) removeAfterInstruction(index);
    }

    public void removeInstruction(IceInstruction instruction) {
        instructions.remove(instruction);
    }

    public List<IceInstruction> getInstructions() {
        return instructions;
    }

    public List<IceInstruction> instructions() {
        return instructions;
    }

    public List<IceBlock> successors() {
        return getSuccessors();
    }

    /**
     * 获取当前基本块的后继基本块
     * @implNote 后继基本块是指当前基本块的最后一条指令的操作数中是基本块的操作数对于ret指令和unreachable指令，后继基本块为空
     * @return 后继基本块列表
     */
    public List<IceBlock> getSuccessors() {
        if (instructions.isEmpty()) return List.of();
        // 取最后一条指令
        final var terminator = instructions.getLast();
        if (terminator.isTerminal()) {
            switch (terminator) {
                case IceBranchInstruction branch -> {
                    return branch.getOperands().stream()
                            .filter(iceUser -> iceUser instanceof IceBlock)
                            .map(iceUser -> (IceBlock) iceUser)
                            .collect(Collectors.toList());
                }
                case IceRetInstruction _, IceUnreachableInstruction _ -> {
                    return List.of();
                }
                default -> throw new IllegalStateException("Unexpected value: " + terminator);
            }
        } else {
            return List.of();
        }
    }

    public List<IceBlock> predecessors() {
        return getPredecessors();
    }

    public List<IceBlock> getPredecessors() {
        return getUsers().stream()
                .filter(iceUser -> iceUser instanceof IceInstruction)
                .map(inst -> ((IceInstruction) inst).getParent()).toList();
    }

    @Deprecated
    public void addSuccessor(IceBlock block) {
        this.addOperand(block);
    }

    @Deprecated
    public void removeSuccessor(IceBlock block) {
        this.removeOperand(block);
    }

    @Override
    public String getReferenceName(boolean withType) {
        return (withType ? "label " : "") + "%" + getName();
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

    public static IceBlock fromTextIR(String textIR, IceFunction parentFunction, Map<String, IceValue> environment) {
        return buildIRParser(textIR).basicBlock().accept(new IceBlockVisitor(parentFunction, environment));
    }

    public static IceBlock fromTextIR(String textIR, IceFunction parentFunction) {
        return buildIRParser(textIR).basicBlock().accept(new IceBlockVisitor(parentFunction, new HashMap<>()));
    }

    @Override
    public void destroy() {
        // 每个指令都要调用 destroy 方法，里面采用了remove方法，为了防止 ConcurrentModificationException 复制一份再删除
        List.copyOf(instructions).forEach(IceInstruction::destroy);
        super.destroy();
    }
}
