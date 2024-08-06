package top.voidc.ir;

import top.voidc.ir.instruction.IceInstruction;
import top.voidc.ir.type.IceType;

import java.util.ArrayList;
import java.util.List;

public class IceFunction extends IceConstant {
    private int valueCounter = 0;
    private final List<IceType> parameterTypes;
    private IceType returnType;

    private final List<IceValue> parameters;

    private final List<IceInstruction> instructions;

    private IceBlock entryBlock = null;

    public IceFunction(String name) {
        super(name, IceType.FUNCTION);
        this.parameterTypes = new ArrayList<>();
        this.parameters = new ArrayList<>();
        this.instructions = new ArrayList<>();
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

    public void insertInstructionFirst(IceInstruction instruction) {
        instructions.add(0, instruction);
    }

    public void addInstruction(IceInstruction instruction) {
        instructions.add(instruction);
    }

    public String generateLocalValueName() {
        return String.valueOf(valueCounter++);
    }

    @Override
    public String toString() {
        final var sb = new StringBuilder();
        sb.append("define ").append(returnType).append(" @").append(getName()).append("(");
        sb.append(String.join(", ", parameters.stream()
                            .map(p -> p.getType() + " %" + p.getName()).toArray(String[]::new)));
        sb.append(')').append(" {\n");

        for (final var instr: instructions) {
            sb.append('\t').append(instr).append('\n');
        }

        sb.append('}');
        return sb.toString();
    }
}
