package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;

import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IcePHINode;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;

import top.voidc.optimizer.pass.DominatorTree;
import top.voidc.optimizer.pass.Pass;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.Queue;
import java.util.ArrayDeque;

/**
 * Memory to Register Promotion
 *
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

            for (IceBlock dominator : dfTable.get(block)) {
                if (!phiTable.containsKey(dominator)) {
                    IceType type = ((IcePtrType<?>) value.getType()).getPointTo();
                    IcePHINode phiNode = new IcePHINode(dominator, value.getName(), type);
                    phiTable.put(dominator, phiNode);
                    dominator.addInstructionAtFront(phiNode);
                }

                IcePHINode phiNode = phiTable.get(dominator);
                if (!phiNode.containsBranch(block)) {
                    phiNode.addBranch(block, findValueStored(block, value));
                }

                if (!workList.contains(dominator)) {
                    workList.add(dominator);
                }
            }
        }
    }

    @Override
    public void run(IceFunction target) {
        ArrayList<IceValue> promotableValues = createPromotableList(target);
        DominatorTree domTree = new DominatorTree(target);
        Hashtable<IceBlock, ArrayList<IceBlock>> dfTable = createDominanceFrontierTable(target, domTree);

        for (IceValue value : promotableValues) {
            insertPhi(value, target, dfTable);
        }
    }
}
