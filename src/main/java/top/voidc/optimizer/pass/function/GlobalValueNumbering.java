package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.optimizer.pass.DominatorTree;

import top.voidc.misc.annotation.Pass;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.LinkedList;

/**
 * GVN - 全局值编号
 * 对每一个表达式进行哈希，合并其中重复的部分
 * 该 pass 应位于 Mem2Reg 下游
 */
@Pass(group = {"O1"})
public class GlobalValueNumbering implements CompilePass<IceFunction> {
    /** 用于作用域管理的栈
     * 由 LinkedList 实现，新入栈的元素位于第 0 个
     */
    private static class ExpressionTableStack {
        LinkedList<Hashtable<Integer, IceInstruction>> stack = new LinkedList<>();

        void pushScope() {
            stack.addFirst(new Hashtable<>());
        }

        void popScope() {
            stack.removeFirst();
        }

        void put(int hash, IceInstruction expr) {
            stack.getFirst().put(hash, expr);
        }

        IceInstruction get(int key) {
            for (var table : stack) {
                if (table.containsKey(key)) {
                    return table.get(key);
                }
            }

            return null;
        }
    }

    private static int hashCombine(int seed, int val) {
        seed ^= val + 0x9e3779b9 + (seed << 6) + (seed >> 2);
        return seed;
    }

    /**
     * 按照一定规则生成标准的 Hash Code，以保证相同的值拥有相同的 Hash Code
     *
     * @param val 被 hash 对象
     * @return Hash Code
     */
    private static int getCanonicalHash(IceValue val) {
        if (val instanceof IceConstantData data) {
            int result;
            switch (data) {
                case IceConstantInt i -> result = ("const int" + i.getValue()).hashCode();
                case IceConstantBoolean b -> result = ("const bool" + b.getValue()).hashCode();
                case IceConstantFloat f -> result = ("const float" + f.getValue()).hashCode();
                case IceConstantDouble d -> result = ("const double" + d.getValue()).hashCode();
                case IceConstantByte by ->  result = ("const byte" + by.getValue()).hashCode();
                default -> result = data.hashCode();
            }

            return result;
        }

        if (val.getType() instanceof IcePtrType<?> ptr) {
            var pointTo = ptr.getPointTo();
            return ("point to " + pointTo.hashCode()).hashCode();
        }

        return val.hashCode();
    }

    /**
     * 返回表达式是否符合二元运算的交换律
     *
     * @param binary 待观测表达式
     * @return true if yes, false otherwise
     */
    private static boolean isCommutativeBinaryExpression(IceBinaryInstruction binary) {
        return binary instanceof IceBinaryInstruction.Add
                || binary instanceof IceBinaryInstruction.FAdd
                || binary instanceof IceBinaryInstruction.Mul
                || binary instanceof IceBinaryInstruction.FMul
                || binary instanceof IceBinaryInstruction.And
                || binary instanceof IceBinaryInstruction.Or
                || binary instanceof IceBinaryInstruction.Xor;
    }

    private static int hashBinaryExpression(IceBinaryInstruction binary) {
        int lhsHash = getCanonicalHash(binary.getLhs());
        int rhsHash = getCanonicalHash(binary.getRhs());
        int opHash = binary.getTypeHash();

        if (isCommutativeBinaryExpression(binary) && lhsHash > rhsHash) {
            int t = lhsHash;
            lhsHash = rhsHash;
            rhsHash = t;
        }

        int h = hashCombine(opHash, lhsHash);
        h = hashCombine(h, rhsHash);
        return h;
    }

    private DominatorTree<IceBlock> dominatorTree = null;
    private ArrayList<IceInstruction> deletedExprs = null;
    ExpressionTableStack exprTable = null;

    private void visitBasicBlock(IceBlock block) {
        exprTable.pushScope();

        for (IceInstruction instr : block) {
            if (instr instanceof IceBinaryInstruction binary) {
                int key = hashBinaryExpression(binary);

                var instead = exprTable.get(key);
                if (instead != null) {
                    binary.replaceAllUsesWith(instead);
                    deletedExprs.add(binary);
                } else {
                    exprTable.put(key, instr);
                }
            }
        }

        for (IceBlock next : dominatorTree.getDominatees(block)) {
            visitBasicBlock(next);
        }

        exprTable.popScope();
    }

    @Override
    public boolean run(IceFunction target) {
        var graph = target.getControlFlowGraph();
        var entryNodeId = graph.getNodeId(target.getEntryBlock());
        dominatorTree = new DominatorTree<>(graph, entryNodeId);
        // 哈希值到指令的映射
        exprTable = new ExpressionTableStack();
        // 经过这次 GVN 之后可以被替换的表达式
        deletedExprs = new ArrayList<>();

        visitBasicBlock(target.getEntryBlock());

        for (IceInstruction instr : deletedExprs) {
            instr.destroy();
        }
        return !deletedExprs.isEmpty();
    }

    @Override
    public String getName() {
        return "Global Value Numbering";
    }
}
