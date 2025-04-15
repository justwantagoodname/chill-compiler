package top.voidc.optimizer.pass.function;

import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.optimizer.pass.DominatorTree;
import top.voidc.optimizer.pass.Pass;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceArrayType;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * Memory to Register Promotion
 *
 * This pass creates SSA IR, and promotes memory accesses to register accesses.
 * This pass will try to delete alloca instructions, and replace them with ice-ir registers.
 */
public class Mem2Reg implements Pass<IceFunction> {
    private static Hashtable<IceBlock, ArrayList<IceBlock>> createDominanceFontierTable(IceFunction function, DominatorTree domTree) {
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

    private static Hashtable<IceValue, Integer> createAllocaTable(IceFunction function) {
        // hashtable: IceValue -> counting
        Hashtable<IceValue, Integer> result = new Hashtable<>();

        for (IceInstruction instr : function.getEntryBlock().getInstructions()) {
            if (instr instanceof IceAllocaInstruction value) {
                IceType type = ((IcePtrType<?>) value.getType()).getPointTo();

                if (type instanceof IceArrayType) {
                    continue;
                }

                result.put(instr, 0);
            }
        }

        return result;
    }

    @Override
    public void run(IceFunction target) {
        Hashtable<IceValue, Integer> allocaTable = createAllocaTable(target);
        DominatorTree domTree = new DominatorTree(target);
        Hashtable<IceBlock, ArrayList<IceBlock>> dfTable = createDominanceFontierTable(target, domTree);
    }
}
