package top.voidc.ir.ice.constant;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.frontend.ir.FunctionVisitor;
import top.voidc.frontend.parser.IceLexer;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceAlignable;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.ds.ChilletGraph;

import java.util.*;

public class IceFunction extends IceConstant implements Iterable<IceBlock>, IceAlignable {

    public static class IceFunctionParameter extends IceValue {

        private final IceFunction function;

        public IceFunctionParameter(IceFunction parent, String name, IceType type) {
            super(name, type);
            this.function = parent;
        }

        public IceFunction getFunction() {
            return function;
        }
    }

    private int tempValueCounter = 0;
    private int blockLabelCounter = 0;
    private final List<IceType> parameterTypes;
    private IceType returnType;

    private final List<IceFunctionParameter> parameters;

    private IceBlock entryBlock;

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

    public List<IceFunctionParameter> getParameters() {
        return parameters;
    }

    public void addParameter(IceFunctionParameter parameter) {
        parameters.add(parameter);
        parameterTypes.add(parameter.getType());
    }

    public String generateLocalValueName() {
        return String.valueOf(tempValueCounter++);
    }

    public String generateLocalValueName(String name) {
        final var ret =  name + (blockLabelCounter == 0 ? "" : blockLabelCounter);
        blockLabelCounter++;
        return ret;
    }

    public String generateLabelName() {
        return String.valueOf(blockLabelCounter++);
    }

    public String generateLabelName(String name) {
        if (name.equals("exit") || name.equals("entry")) {
            return name;
        }
        final var ret = name + (blockLabelCounter == 0 ? "" : blockLabelCounter);
        blockLabelCounter++;
        return ret;
    }


    private void listBlocks(Set<IceBlock> visited, List<IceBlock> result, IceBlock currentBlock) {
        if (visited.contains(currentBlock)) return;
        visited.add(currentBlock);
        currentBlock.successors().forEach(block -> {
            if (!visited.contains(block)) listBlocks(visited, result, block);
        });
        result.add(currentBlock);
    }

    /**
     * Get all blocks in the function.
     * 以逆后序遍历顺序打印
     * @return 当前函数的所有基本块
     */
    public List<IceBlock> blocks() {
        final var blockSet = new HashSet<IceBlock>();
        final var result = new ArrayList<IceBlock>();
        listBlocks(blockSet, result, getEntryBlock());
        Collections.reverse(result);
        return result;
    }

    public int getBlocksSize() {
        return blocks().size();
    }

    public IceBlock getEntryBlock() {
        return entryBlock;
    }

    public void setEntryBlock(IceBlock block) {
        this.entryBlock = block;
    }

    public IceBlock getExitBlock() {
        return exitBlock;
    }

    public List<IceBlock> getBlocks() {
        return blocks();
    }

    public List<IceBlock> getBFSBlocks() {
        ArrayList<IceBlock> result = new ArrayList<>();
        Set<IceBlock> visited = new HashSet<>();

        Queue<IceBlock> queue = new LinkedList<>();
        queue.add(getEntryBlock());
        while (!queue.isEmpty()) {
            IceBlock current = queue.poll();
            if (visited.contains(current)) continue;
            visited.add(current);
            result.add(current);
            for (IceBlock successor : current.successors()) {
                if (!visited.contains(successor)) {
                    queue.add(successor);
                }
            }
        }

        return result;
    }

    @Override
    public String getReferenceName(boolean withType) {
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
        blocks().forEach(block -> {
            block.getTextIR(builder);
            builder.append("\n");
        });
        builder.append("}");
    }

    /**
     * 从LLVM IR格式的文本创建函数
     * 
     * <p>示例：</p>
     * <pre>
     * define i32 @add(i32 %a, i32 %b) {
     *     %entry:
     *         %sum = add i32 %a, %b
     *         ret i32 %sum
     * }
     * </pre>
     *
     * @param textIR LLVM IR格式的函数声明文本
     * @return 创建的函数实例
     * @throws IllegalArgumentException 如果IR文本格式不正确
     */
    public static IceFunction fromTextIR(String textIR) {
        var irStream = CharStreams.fromString(textIR);
        var tokenStream = new CommonTokenStream(new IceLexer(irStream));
        var parser = new IceParser(tokenStream);
        return parser.functionDecl().accept(new FunctionVisitor());
    }

    /**
     * 从LLVM IR格式的文本创建函数，并将相关标识符添加到提供的环境中
     * 
     * <p>本方法会将以下内容添加到环境中：</p>
     * <ul>
     *   <li>函数本身 - 使用函数名作为键</li>
     *   <li>函数参数 - 使用参数名作为键</li>
     *   <li>基本块 - 使用基本块标签作为键</li>
     * </ul>
     *
     * @param textIR LLVM IR格式的函数声明文本
     * @param environment 用于存储标识符的环境映射
     * @return 创建的函数实例
     * @throws IllegalArgumentException 如果IR文本格式不正确
     */
    public static IceFunction fromTextIR(String textIR, Map<String, IceValue> environment) {
        var irStream = CharStreams.fromString(textIR);
        var tokenStream = new CommonTokenStream(new IceLexer(irStream));
        var parser = new IceParser(tokenStream);
        return parser.functionDecl().accept(new FunctionVisitor(environment));
    }

    /**
     * Returns an iterator over elements of type {@code T}.
     *
     * @return an Iterator.
     */
    @Override
    public Iterator<IceBlock> iterator() {
        return blocks().iterator();
    }

    @Override
    public int getAlignment() {
        return 4; // 默认对齐方式到4字节
    }

    public ChilletGraph<IceBlock> getControlFlowGraph() {
        var blocks = blocks();
        var graph = new ChilletGraph<IceBlock>(blocks.size());
        graph.createNewNodes(blocks);
        

        for (var block : blocks) {
            graph.addEdges(block, block.successors());
        }
        return graph;
    }
}
