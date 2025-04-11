package top.voidc.ir.ice.constant;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceType;

import java.util.ArrayList;
import java.util.List;

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

    public Iterable<IceBlock> blocks() {
        final var blocks = new ArrayList<IceBlock>();
        blocks.add(entryBlock);

        for (var i = 0; i < blocks.size(); i++) {
            final var currentBlock = blocks.get(i);
            currentBlock.successors().forEach(block -> {
                if (!blocks.contains(block)) {
                    blocks.add(block);
                }
            });
        }

        return blocks;
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("define ").append(returnType).append(" @").append(getName()).append("(");
        sb.append(String.join(", ", parameters.stream()
                            .map(p -> p.getType() + " %" + p.getName()).toArray(String[]::new)));
        sb.append(") {\n");
        blocks().forEach(sb::append);
        sb.append('}');
        return sb.toString();
    }

    public IceBlock getEntryBlock() {
        return entryBlock;
    }

    public IceBlock getExitBlock() {
        return exitBlock;
    }

    public String getSignature() {
        return "@" + getName() + "(" +
                String.join(", ", parameters.stream()
                        .map(p -> p.getType().toString()).toArray(String[]::new)) +
                ")";
    }
}
