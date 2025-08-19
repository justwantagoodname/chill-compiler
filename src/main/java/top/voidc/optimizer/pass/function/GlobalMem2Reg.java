package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.constant.IceUndef;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.annotation.Qualifier;
import top.voidc.misc.ds.ChilletGraph;
import top.voidc.misc.ds.DominatorTree;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.optimizer.pass.unit.FunctionPureness;

import java.util.*;

@Pass(group = {"00"})
public class GlobalMem2Reg implements CompilePass<IceFunction> {
    private final IceContext context;
    private final Map<IceFunction, FunctionPureness.PurenessInfo> purenessInfoMap;

    public GlobalMem2Reg(IceContext context, @Qualifier("functionPureness") Map<IceFunction, FunctionPureness.PurenessInfo> purenessInfoMap) {
        this.purenessInfoMap = purenessInfoMap;
        this.context = context;
    }
//    @Override
//    public boolean run(IceFunction target) {
//
//        // 构建支配树
//        ChilletGraph<IceBlock> graph = target.getControlFlowGraph();
//        DominatorTree<IceBlock> domTree = new DominatorTree<>(graph, graph.getNodeId(target.getEntryBlock()));
//
//        // 遍历支配树
//        dfsTree(domTree, target.getEntryBlock());
//        return false;
//    }
//
//    private void dfsTree(DominatorTree<IceBlock> domTree, IceBlock entry){
//        var doms = domTree.getDominatees(entry);
//        for(var dom: doms){
//            dfsTree(domTree, dom);
//        }
//    }

    /**
     * 为给定函数创建支配边界表。
     * <br>
     * for each node X in the CFG:
     *     if X has >= 2 predecessors:
     *         for each predecessor P of X:
     *             runner = P
     *             while runner != idom(X):
     *                 DF[runner].add(X)
     *                 runner = idom(runner)
     *
     * @param function 用于创建支配边界表的函数
     * @param domTree 函数的支配树
     * @return 用于创建支配边界表的函数
     */
    private static Hashtable<IceBlock, ArrayList<IceBlock>> createDominanceFrontierTable(IceFunction function, DominatorTree<IceBlock> domTree) {
        Hashtable<IceBlock, ArrayList<IceBlock>> result = new Hashtable<>();
        for (IceBlock x : function.getBlocks()) {
            result.put(x, new ArrayList<>());
        }

        for (IceBlock x : function.getBlocks()) {
            IceBlock dominator = domTree.getDominator(x);

            if (x.getPredecessors().size() >= 2) {
                for (IceBlock p : x.getPredecessors()) {
                    IceBlock runner = p;
                    while (runner != dominator) {
                        result.get(runner).add(x);
                        runner = domTree.getDominator(runner);
                    }
                }
            }
        }

        return result;
    }

    /**
     * 创建一个可以提升到寄存器的所有值的列表。
     * 扫描函数并获取每一个被load过的全局变量
     *
     *@param function 用于创建列表的函数
     *@return 可以提升的值列表
     */
    // TODO 获取所有非数组的全局变量
    private List<IceGlobalVariable> createPromotableList(IceFunction function) {

        List<IceGlobalVariable> list = new ArrayList<>();

        for (var block : function){
            for(var instr : block){
                if(instr instanceof IceLoadInstruction load){
                    if(load.getOperand(0) instanceof IceGlobalVariable globalVar){
                        list.add(globalVar);
                    }
                }
            }
        }
        return list;
    }

    private IceValue findValueStored(IceBlock block, IceValue value) {
        for (IceInstruction instr : block) {
            if (instr instanceof IceStoreInstruction store) {
                if (store.getTargetPtr() == value) {
                    return store.getValue();
                }
            }
        }
        return null;
    }

    private void insertPhi(IceValue value, IceFunction function, Hashtable<IceBlock, ArrayList<IceBlock>> dfTable) {
        // pair for workList queue
        // block: the block that stores the value, value: the value to be stored
        Queue<IceBlock> workList = new ArrayDeque<>();

        // phi node for each block, value: phi node if the block has already inserted a phi node
        // for the given value, null if not
        Hashtable<IceBlock, IcePHINode> phiTable = new Hashtable<>();

        // add all blocks that store value to workList
        for (IceBlock block : function.getBlocks()) {
            for (IceInstruction instr : block) {
                if (instr instanceof IceStoreInstruction store) {
                    if (store.getTargetPtr() == value) {
                        workList.add(block);
                    }
                }
            }
        }

        while (!workList.isEmpty()) {
            IceBlock block = workList.poll();

            for (IceBlock dominatee : dfTable.get(block)) {
//                System.out.println("Current block: " + dominator.getName());
                if (!phiTable.containsKey(dominatee)) {
                    IceType type = ((IcePtrType<?>) value.getType()).getPointTo();
                    IcePHINode phiNode = new IcePHINode(dominatee, value.getName(), type);
                    phiNode.setValueToBeMerged(value);

                    phiTable.put(dominatee, phiNode);
                    dominatee.addInstructionAtFront(phiNode);

                    if (findValueStored(dominatee, value) == null) {
                        workList.add(dominatee);
                    }
                }
            }
        }
    }

    private IceValue createNewName(IceBlock block, IceValue value) {
        // Note: %1.1这种名字不合法，先生成一个新的名字，后续使用 RenameVariable Pass 来改名
        String newName = block.getFunction().generateLocalValueName();
        // 这里需要注意的是，value.getType() 返回的是指针类型
        // 因此需要将其转换为指向的类型
        IceType type = ((IcePtrType<?>) value.getType()).getPointTo();
        return new IceValue(newName, type);
    }

    private void rename(IceBlock block, Hashtable<IceValue, Stack<IceValue>> valueStack, DominatorTree<IceBlock> domTree) {
        // 本层递归中，每种变量历史版本的计数器用于恢复栈
        final var defCounter = new HashMap<IceValue, Integer>();
        valueStack.forEach((key, value) -> defCounter.put(key, value.size()));

        // 用迭代器正确处理边遍历器删除元素的问题
        final var blockInstruction = block.iterator();
        while (blockInstruction.hasNext()){
            IceInstruction instr = blockInstruction.next();
            switch (instr) {
                case IceLoadInstruction load -> {
                    IceValue value = load.getSource();
                    if (valueStack.containsKey(value)) {
                        // 如果是第一次load或call后第一次load，保留
                        if (valueStack.get(value).isEmpty()
                                || valueStack.get(value).peek() instanceof IceCallInstruction) {
                            // 压入栈，作为初始变量
                            valueStack.get(value).push(load.getSource());
                        } else {
                            // 如果当前 load 的源指针在 valueStack 中，则获取栈顶元素并更新 load 的源指针
                            IceValue nextValue = valueStack.get(value).peek();

                            // 直接替换 load 指令的使用
                            load.replaceAllUsesWith(nextValue);

                            // 删除 load 指令
                            blockInstruction.remove();
                            load.destroy();
                        }
                    }
                }
                case IceStoreInstruction store -> {
                    // 获取当前 store 的目标指针
                    IceValue value = store.getTargetPtr();
                    if (valueStack.containsKey(value)) {
                        // 记录变量别名：store 相当于将 目标地址的变量 设置别名为 源变量
                        // 因此，将新的别名压入栈中
                        valueStack.get(value).push(store.getValue());

                        // 删除 store 指令
                        blockInstruction.remove();
                        store.destroy();
                    }
                }
                case IceCallInstruction call -> {
                    var info = purenessInfoMap.get(call.getTarget());
                    if(info.)
                }
                case IceRetInstruction ret -> {
                    // store最后单独值
                }
                case null -> throw new IllegalStateException(); // This should never happen
                default -> {
                    // 如果是其它类型的指令，什么也不做
                }
            }
        }

        // 为所有 successor block 中的 phi 指令添加参数
        for (IceBlock successor : block.getSuccessors()) {
            for (IceInstruction instr : successor) {
                // 所有 phi 指令都应当在 block 的开头
                // 因此，如果遇到第一个不是 phi 指令的指令，则说明已经处理结束
                if (!(instr instanceof IcePHINode phiNode)) {
                    break;
                }

                // 如果当前 phi 指令的 valueToBeMerged 在 valueStack 中，则获取栈顶元素并添加到 phi 指令的参数中
                if (valueStack.containsKey(phiNode.getValueToBeMerged())) {
                    IceValue nextValue = valueStack.get(phiNode.getValueToBeMerged()).peek();
                    phiNode.addBranch(block, nextValue);
                }
            }
        }

        // 按照 Dominator Tree 顺序 dfs
        for (IceBlock successor : domTree.getDominatees(block)) {
            rename(successor, valueStack, domTree);
        }

        // 处理完所有的 successor block 后，pop 出当前 block 中所有的 store 的新版本
        defCounter.forEach((value, oldCount) -> {
            final var stack = valueStack.get(value);
            while (stack.size() > oldCount) stack.pop();
        });
    }

    @Override
    public boolean run(IceFunction target) {
        final var promotableValues = createPromotableList(target);
        var graph = target.getControlFlowGraph();
        var entryNodeId = graph.getNodeId(target.getEntryBlock());
        var domTree = new DominatorTree<>(graph, entryNodeId);
        Hashtable<IceBlock, ArrayList<IceBlock>> dfTable = createDominanceFrontierTable(target, domTree);

        Hashtable<IceValue, Stack<IceValue>> valueStack = new Hashtable<>();
        for (var value : promotableValues) {
            insertPhi(value, target, dfTable);
            valueStack.put(value, new Stack<>());
            IceType type = value.getType();
            valueStack.get(value).push(IceUndef.get(type));
        }

        rename(target.getEntryBlock(), valueStack, domTree);

        // 删除所有的 alloca
        promotableValues.forEach(value -> {
            Log.should(value.users().isEmpty(), "There are still users for %" + value.getName() + " after mem2reg");
            value.destroy();
        });

        return !promotableValues.isEmpty(); // 只要有一个变量被提升，就肯定修改IR了
    }

    @Override
    public String getName() {
        return "GlobalMem2Reg";
    }

}
