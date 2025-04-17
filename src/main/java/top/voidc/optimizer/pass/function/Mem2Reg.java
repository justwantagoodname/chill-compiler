package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;

import top.voidc.ir.ice.constant.IceUndef;
import top.voidc.ir.ice.instruction.*;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;

import top.voidc.optimizer.pass.DominatorTree;
import top.voidc.optimizer.pass.Pass;

import java.util.*;

/**
 * Memory to Register Promotion
 * <br>
 * This pass creates SSA IR, and promotes memory accesses to register accesses.
 * This pass will try to delete alloca instructions, and replace them with ice-ir registers.
 */
public class Mem2Reg implements Pass<IceFunction> {
    /**
     * Create a dominance frontier table for the given function.
     * <br>
     * for each node X in the CFG:
     *     if X has >= 2 predecessors:
     *         for each predecessor P of X:
     *             runner = P
     *             while runner != idom(X):
     *                 DF[runner].add(X)
     *                 runner = idom(runner)
     *
     * @param function the function to create the dominance fontier table for
     * @param domTree the dominator tree of the function
     * @return the dominance frontier table
     */
    private static Hashtable<IceBlock, ArrayList<IceBlock>> createDominanceFrontierTable(IceFunction function, DominatorTree domTree) {
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
     * Create a list of all values that can be promoted to registers.
     * Scanning the function's entry block for alloca instructions.
     *
     * @param function the function to create the list for
     * @return the list of values that can be promoted
     */
    private static ArrayList<IceValue> createPromotableList(IceFunction function) {
        ArrayList<IceValue> result = new ArrayList<>();

        for (IceInstruction instr : function.getEntryBlock().getInstructions()) {
            if (instr instanceof IceAllocaInstruction value) {
                IceType type = ((IcePtrType<?>) value.getType()).getPointTo();

                if (type instanceof IceArrayType) {
                    continue;
                }

                result.add(instr);
            }
        }

        return result;
    }

    private static IceValue findValueStored(IceBlock block, IceValue value) {
        for (IceInstruction instr : block.getInstructions()) {
            if (instr instanceof IceStoreInstruction store) {
                if (store.getTargetPtr() == value) {
                    return store.getValue();
                }
            }
        }
        return null;
    }

    private static void insertPhi(IceValue value, IceFunction function, Hashtable<IceBlock, ArrayList<IceBlock>> dfTable) {
        // pair for workList queue
        // block: the block that stores the value, value: the value to be stored
        Queue<IceBlock> workList = new ArrayDeque<>();

        // phi node for each block, value: phi node if the block has already inserted a phi node
        // for the given value, null if not
        Hashtable<IceBlock, IcePHINode> phiTable = new Hashtable<>();

        // add all blocks that store value to workList
        for (IceBlock block : function.getBlocks()) {
            for (IceInstruction instr : block.getInstructions()) {
                if (instr instanceof IceStoreInstruction store) {
                    if (store.getTargetPtr() == value) {
                        workList.add(block);
                    }
                }
            }
        }

        // TODO: need to be check!
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

    // 重命名计数器
    private static int renameCounter = 0;

    private static IceValue createNewName(IceValue value) {
        String newName = value.getName() + "." + renameCounter++;
        // 这里需要注意的是，value.getType() 返回的是指针类型
        // 因此需要将其转换为指向的类型
        IceType type = ((IcePtrType<?>) value.getType()).getPointTo();
        return new IceValue(newName, type);
    }

    private static void rename(IceBlock block, Hashtable<IceValue, Stack<IceValue>> valueStack, DominatorTree domTree, Hashtable<IceValue, IceValue> aliasTable) {
        // 本层递归中，每种变量的 store 数量计数器
        Hashtable<IceValue, Integer> defCounter = new Hashtable<>();

        List<IceInstruction> blockInstructions = block.getInstructions();
        for (int index = 0; index < blockInstructions.size(); ++index) {
            IceInstruction instr = blockInstructions.get(index);
            if (instr instanceof IceStoreInstruction store) {
                // 获取当前 store 的目标指针
                IceValue value = store.getTargetPtr();
                if (valueStack.containsKey(value)) {
                    // 记录变量别名：store 相当于将 目标地址的变量 设置别名为 源变量
                    // 因此，将新的别名压入栈中
                    valueStack.get(value).push(store.getValue());

                    // 删除 store 指令
                    block.removeInstruction(instr);

                    // 修正 index
                    // 因为删除了 store 指令，后面的指令会向前移动
                    --index;

                    // 更新 defCounter
                    defCounter.put(value, defCounter.getOrDefault(value, 0) + 1);
                }
            } else if (instr instanceof IceLoadInstruction load) {
                IceValue value = load.getSource();
                if (valueStack.containsKey(value)) {
                    // 如果当前 load 的源指针在 valueStack 中，则获取栈顶元素并更新 load 的源指针
                    IceValue nextValue = valueStack.get(value).peek();

                    // 记录变量别名：load 相当于给 源变量 设置别名
                    aliasTable.put(load, nextValue);

                    // 删除 load 指令
                    // 注意：在此处只是删除了 load 指令，实际对象应当仍然是存在的，以供后续改名
                    block.removeInstruction(instr);

                    // 修正 index
                    // 因为删除了 load 指令，后面的指令会向前移动
                    --index;
                }
            } else if (instr instanceof IcePHINode phiNode) {
                // 如果当前指令是 phi 指令，则获取其 valueToBeMerged
                IceValue value = phiNode.getValueToBeMerged();
                if (valueStack.containsKey(value)) {
                    // 如果当前 phi 指令的 valueToBeMerged 在 valueStack 中，则获取栈顶元素并更新 phi 指令的源指针
                    IceValue nextValue = createNewName(value);
                    // 由于 phi 指令的 valueToBeMerged 追踪的是原本的指针，因此不修改
                    // 但是需要修改 phi 指令的名字
                    phiNode.setName(nextValue.getName());

                    // 修改 phi 指令的名字后，相当于新产生的变量就是这个 phi 指令
                    valueStack.get(value).push(phiNode);
                }
            } else  {
                // 如果是其它类型的指令，应当对其所有变量改名
                // 检查其中是否有 存在别名 的变量，如果有，修改
                List<IceValue> operands = instr.getOperandsList();
                for (int i = 0; i < operands.size(); i++) {
                    IceValue operand = operands.get(i);
                    if (aliasTable.containsKey(operand)) {
                        IceValue alias = aliasTable.get(operand);
                        instr.setOperand(i, alias);
                    }
                }
            }
        }

        // 为所有 successor block 中的 phi 指令添加参数
        for (IceBlock successor : block.getSuccessors()) {
            for (IceInstruction instr : successor.getInstructions()) {
                // 所有 phi 指令都应当在 block 的开头
                // 因此，如果遇到第一个不是 phi 指令的指令，则说明已经处理结束
                if (!(instr instanceof IcePHINode phiNode)) {
                    break;
                }

                // 如果当前 phi 指令的 valueToBeMerged 在 valueStack 中，则获取栈顶元素并添加到 phi 指令的参数中
                if (valueStack.containsKey(phiNode.getValueToBeMerged())) {
                    IceValue nextValue = valueStack.get(phiNode.getValueToBeMerged()).peek();

                    // 如果变量有别名，应当将别名添加到 phi 指令的参数中
                    if (aliasTable.containsKey(nextValue)) {
                        nextValue = aliasTable.get(nextValue);
                    }

                    phiNode.addBranch(block, nextValue);
                }
            }
        }

        // 按照 Dominator Tree 顺序 dfs
        for (IceBlock successor : domTree.getDominatees(block)) {
            rename(successor, valueStack, domTree, aliasTable);
        }

        // 处理完所有的 successor block 后，pop 出当前 block 中所有的 store 的新版本
        for (IceValue value : defCounter.keySet()) {
            int count = defCounter.get(value);
            Stack<IceValue> stack = valueStack.get(value);

            // pop 出当前 block 中所有的 store 的新版本
            for (int i = 0; i < count; i++) {
                stack.pop();
            }
        }
    }

    @Override
    public void run(IceFunction target) {
        ArrayList<IceValue> promotableValues = createPromotableList(target);
        DominatorTree domTree = new DominatorTree(target);
        Hashtable<IceBlock, ArrayList<IceBlock>> dfTable = createDominanceFrontierTable(target, domTree);

        Hashtable<IceValue, Stack<IceValue>> valueStack = new Hashtable<>();
        for (IceValue value : promotableValues) {
            insertPhi(value, target, dfTable);
            valueStack.put(value, new Stack<>());
            IceType type = ((IcePtrType<?>) value.getType()).getPointTo();
            valueStack.get(value).push(IceUndef.get(type));
        }

        // rename 中，每个 store, load 相当于为变量产生了一个别名
        // 这个表用来记录别名
        Hashtable<IceValue, IceValue> aliasTable = new Hashtable<>();
        rename(target.getEntryBlock(), valueStack, domTree, aliasTable);

        // 删除所有的 alloca
        for (IceValue value : promotableValues) {
            if (value instanceof IceAllocaInstruction instr) {
                // 删除 alloca 指令
                instr.getParent().removeInstruction(instr);
            } else {
                throw new RuntimeException("Unexpected value type when removing alloca in mem2reg: " + value.getClass());
            }
        }
    }
}
