package top.voidc.ir.ice.constant;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.*;

public class IceFunction extends IceConstant {
    private int tempValueCounter = 0;
    private final List<IceType> parameterTypes;
    private IceType returnType;

    private final List<IceValue> parameters;

    private final IceBlock entryBlock;

    private final IceBlock exitBlock;

    public IceFunction(String name) {
        super(name, IceType.FUNCTION);
        this.entryBlock = new IceBlock(this, "entry");
        this.exitBlock = new IceBlock(this, "exit");
        this.parameterTypes = new ArrayList<>();
        this.parameters = new ArrayList<>();
    }

    public void setReturnType(IceType returnType) {
        this.returnType = returnType;
    }

    public IceType getReturnType() {
        return returnType;
    }

    public List<IceType> getParameterTypes() {
        return parameterTypes;
    }

    public List<IceValue> getParameters() {
        return parameters;
    }

    public void addParameter(IceValue parameter) {
        parameters.add(parameter);
        parameterTypes.add(parameter.getType());
    }

    public String generateLocalValueName() {
        return String.valueOf(tempValueCounter++);
    }

    /**
     * Get all blocks in the function.
     * @return 当前函数的所有基本块
     */
    public List<IceBlock> blocks() {
        final var blockSet = new HashSet<IceBlock>();
        final var result = new ArrayList<IceBlock>();
        final Queue<IceBlock> blockQueue = new ArrayDeque<>();
        blockQueue.add(entryBlock);
        result.add(entryBlock);
        blockSet.add(entryBlock);

        while (!blockQueue.isEmpty()) {
            final var currentBlock = blockQueue.poll();
            currentBlock.successors().forEach(block -> {
                if (!blockSet.contains(block)) {
                    result.add(block);
                    blockQueue.add(block);
                    blockSet.add(block);
                }
            });
        }

        return result;
    }

    public int getBlocksSize() {
        return blocks().size();
    }

    public IceBlock getEntryBlock() {
        return entryBlock;
    }

    public IceBlock getExitBlock() {
        return exitBlock;
    }

    public List<IceBlock> getBlocks() {
        return blocks();
    }

    @Override
    public String getReferenceName() {
        return "@" + getName() + "(" +
                String.join(", ",
                        parameters.stream()
                                .map(IceValue::getType)
                                .map(IceType::toString)
                                .toList()) +
                ")";
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("define ")
                .append(returnType)
                .append(' ')
                .append("@").append(getName())
                .append("(")
                .append(String.join(", ",
                        parameters.stream()
                                .map(IceValue::getReferenceName)
                                .toList()))
                .append(") {\n");
        blocks().forEach(block -> block.getTextIR(builder));
        builder.append("\n}");
    }
}
