package top.voidc.ir.machine;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceType;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 机器指令映射，没有具体的类，全部由 InstructionPattern 生成
 * 指令模版形如 ADD {dst}, {x}, {y}, {imm8:z}
 * 操作数不允许重名
 * 特殊寄存器名dst为结果寄存器
 * 立即数之后设计
 * 操作数布局和指令顺序一致
 */
public abstract class IceMachineInstruction extends IceInstruction implements IceArchitectureSpecification {
    private final String renderTemplate;
    private record NamedOperand(String placeholder, String prefix, int position) {}

    private final Map<String, NamedOperand> namedOperandPosition = new HashMap<>();

    public IceMachineInstruction(String renderTemplate) {
        super(null, null, IceType.VOID);
        this.renderTemplate = renderTemplate;
        parserNamedOperandPosMap();
    }

    public IceMachineInstruction(String renderTemplate, IceValue... values) {
        super(null, null, IceType.VOID);
        this.renderTemplate = renderTemplate;
        Arrays.stream(values).forEachOrdered(this::addOperand);
        parserNamedOperandPosMap();
    }

    private void parserNamedOperandPosMap() {
        // Parse named operands from template
        // Example: ADD {dst}, {x}, {y}, {imm8:z}
        String text = renderTemplate;
        int position = 0;
        
        // Find all occurrences of {name} or {prefix:name}
        int startIndex = 0;
        while ((startIndex = text.indexOf('{', startIndex)) != -1) {
            int endIndex = text.indexOf('}', startIndex);
            if (endIndex == -1) break;
            
            String content = text.substring(startIndex + 1, endIndex);
            String prefix = "";
            String name = content;
            
            // Check if operand has a prefix (like imm8:z)
            if (content.contains(":")) {
                String[] prefixAndName = content.split(":", 2);
                prefix = prefixAndName[0];
                name = prefixAndName[1];
            }

            String placeholder = "{" + (prefix.isEmpty() ? name : prefix + ":" + name) + "}";
            namedOperandPosition.put(name, new NamedOperand(placeholder, prefix, position));
            position++;
            
            startIndex = endIndex + 1;
        }
    }

    @Override
    public void addOperand(IceValue operand) {
        assert operand instanceof IceConstantData || operand instanceof IceMachineRegister;
        super.addOperand(operand);
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        String result = renderTemplate;
        for (var entry : namedOperandPosition.entrySet()) {
            String name = entry.getKey();
            var namedOperand = entry.getValue();
            int pos = namedOperand.position();
            
            // 如果没有足够的操作数则抛出异常
            if (pos >= getOperands().size()) {
                throw new IndexOutOfBoundsException("指令模板的操作数不足: " + renderTemplate +
                        "。位置 " + pos + " 处缺少命名操作数 '" + name + "'");
            }
            
            IceValue operand = getOperand(pos);
            String operandText = operand.getReferenceName();
            
            result = result.replace(namedOperand.placeholder(), operandText);
        }

        builder.append(result);
    }

    public String getOpcode() {
        return renderTemplate.split("\\w+")[0].trim().toUpperCase();
    }

    public IceMachineRegister getResultReg() {
        var position = namedOperandPosition.get("dst");
        if (position == null) return null;
        return (IceMachineRegister) getOperand(position.position());
    }
}
