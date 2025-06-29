package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.instruction.IceBinaryInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;

import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.optimizer.pass.DominatorTree;

import top.voidc.misc.annotation.Pass;

import java.util.ArrayList;
import java.util.Hashtable;

/**
 * GVN - 全局值编号
 * 对每一个表达式进行哈希，合并其中重复的部分
 * 该 pass 应位于 Mem2Reg 下游
 */
@Pass
public class GlobalValueNumbering implements CompilePass<IceFunction> {
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
            int result = 0;
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

    private static int hashBinaryExpression(IceBinaryInstruction binary) {
        assert false;
        return 0;
    }

    @Override
    public boolean run(IceFunction target) {
        DominatorTree dominatorTree = new DominatorTree(target);
        // 哈希值到指令的映射
        Hashtable<Integer, IceInstruction> exprTable = new Hashtable<>();
        // 经过这次 GVN 之后可以被替换的表达式
        ArrayList<IceInstruction> deletedExprs = new ArrayList<>();

        for (IceBlock block : target.getBlocks()) {
            for (IceInstruction instr : block) {
                if (instr instanceof IceBinaryInstruction binary) {
                    int key = hashBinaryExpression(binary);

                    if (exprTable.containsKey(key)) {
                        binary.replaceAllUsesWith(exprTable.get(key));
                        deletedExprs.add(binary);
                    } else {
                        exprTable.put(key, instr);
                    }
                }
            }
        }

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
