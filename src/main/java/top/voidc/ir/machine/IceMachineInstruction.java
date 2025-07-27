package top.voidc.ir.machine;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;

import java.util.*;

/**
 * 机器指令映射，没有具体的类，全部由 InstructionPattern 生成
 * 指令模版形如 ADD {dst}, {x}, {y}, {imm8:z}
 * 操作数不允许重名
 * 特殊寄存器名dst为结果寄存器
 * 立即数之后设计
 * 操作数布局和指令顺序一致
 */
public abstract class IceMachineInstruction extends IceInstruction {
    protected final String renderTemplate;
    protected record NamedOperand(String placeholder, String prefix, int position) {}

    protected final Map<String, NamedOperand> namedOperandPosition = new HashMap<>();

    public IceMachineInstruction(String renderTemplate) {
        super(null, null, IceType.VOID);
        this.renderTemplate = renderTemplate;
        parserNamedOperandPosMap();
    }

    public IceMachineInstruction(String renderTemplate, IceMachineValue... values) {
        super(null, null, IceType.VOID);
        this.renderTemplate = renderTemplate;
        Arrays.stream(values).map(machineValue -> (IceValue) machineValue).forEachOrdered(this::addOperand);
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
        assert operand instanceof IceMachineValue;
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
            String operandText = switch (entry.getValue().prefix()) {
                case "imm" -> {
                    assert operand instanceof IceConstantInt;
                    var intValue = ((IceConstantInt) operand).getValue();
                    yield String.valueOf(intValue); // 直接输出整数值
                }
                case "imm16" -> {
                    assert operand instanceof IceConstantInt;
                    var intValue = ((IceConstantInt) operand).getValue();
                    yield "#" + (intValue & 0xFFFF);
                }
                case "imm12" -> {
                    assert operand instanceof IceConstantInt;
                    var intValue = ((IceConstantInt) operand).getValue();
                    yield "#" + (intValue & 0xFFF);
                }
                case "imm8" -> {
                    assert operand instanceof IceConstantInt;
                    var intValue = ((IceConstantInt) operand).getValue();
                    yield "#" + (intValue & 0xFF);
                }
                case "local" -> {
                    assert operand instanceof IceStackSlot;
                    yield "[sp, #" + ((IceStackSlot) operand).getOffset() + "]"; // TODO 平台加载
                }
                case "local-offset" -> {
                    assert operand instanceof IceStackSlot;
                    try {
                        yield "#" + ((IceStackSlot) operand).getOffset();
                    } catch (IllegalStateException ignored) {
                        yield "slot_uninitialized"; // 如果未初始化，返回占位符
                    }
                }
                case "label" -> operand.getName();
                default -> operand.getReferenceName();
            };
            
            result = result.replace(namedOperand.placeholder(), operandText);
        }

        builder.append(result);
    }

    /**
     * 用循环实现以提高性能
     */
    public String getOpcode() {
        int len = renderTemplate.length();
        int i = 0;
        // 跳过前导空白
        while (i < len && Character.isWhitespace(renderTemplate.charAt(i))) {
            i++;
        }
        int start = i;
        // 找到第一个空白字符
        while (i < len && !Character.isWhitespace(renderTemplate.charAt(i))) {
            i++;
        }
        return renderTemplate.substring(start, i).toUpperCase();
    }

    public IceMachineRegister.RegisterView getResultReg() {
        var position = namedOperandPosition.get("dst");
        if (position == null) return null;
        return (IceMachineRegister.RegisterView) getOperand(position.position());
    }

    /**
     * 获取指令的所有输入操作数
     * @return 获取
     */
    public List<IceValue> getSourceOperands() {
        var results = new ArrayList<IceValue>();
        for (var entry : namedOperandPosition.entrySet()) {
            if (!entry.getKey().equals("dst")) {
                results.add(getOperand(entry.getValue().position()));
            }
        }
        return results;
    }

    public abstract IceMachineInstruction clone();
}
