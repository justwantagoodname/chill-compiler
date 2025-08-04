package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.IceAllocaInstruction;
import top.voidc.ir.ice.instruction.IceGEPInstruction;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IceType;

import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

/**
 * 标量替换聚合
 * 将 聚合类型 的变量（数组）替换为多个标量变量
 * 收益好像不是很大？还需要修复吗？
 * TODO: 这玩意会导致前端测试 RE 60，开了 GVN mem2reg RV SROA SCSCFG SCCP
 */
@Pass(
        group = {"O1", "needfix"}
)
public class ScalarReplacementOfAggregates implements CompilePass<IceFunction> {
    private static ArrayList<IceAllocaInstruction> createPromotableList(IceFunction function) {
        ArrayList<IceAllocaInstruction> result = new ArrayList<>();
        for (IceBlock block : function.getBlocks()) {
            for (IceInstruction instr : block) {
                if (instr instanceof IceAllocaInstruction alloca) {
                    IceType type = alloca.getType().getPointTo();

                    if (!(type instanceof IceArrayType arrayType)) {
                        continue;
                    }

                    if (arrayType.getDimSize() > 1) {
                        // 长大后再学习
                        continue;
                    }
                    if (arrayType.getTotalSize() > 10) {
                        // 太大的数组不支持，防止代码过于复杂
                        continue;
                    }

                    result.add(alloca);
                } else if (instr instanceof IceGEPInstruction gep) {
                    // 这个 pass 只能处理被 静态访问 的数组，因此需要检查 gep 的下标是不是全为 constant
                    List<IceValue> indices = gep.getOperands();
                    for (int i = 1; i < indices.size(); i++) {
                        IceValue index = indices.get(i);
                        if (!(index instanceof IceConstant)) {
                            // 如果下标不是常量，则删除对应的 alloca
                            IceValue basePtr = gep.getBasePtr();
                            if (basePtr instanceof IceAllocaInstruction alloca) {
                                result.remove(alloca);
                            }
                            break;
                        }
                    }
                }
            }
        }
        return result;
    }

    /**
     * 在 function 开头展开 alloca 指令，并返回新的指令列表
     *
     * @param aggregate 聚合类型的 alloca 指令
     * @return 新的指令列表
     */
    private ArrayList<IceValue> aggregatesExpansion(IceAllocaInstruction aggregate) {
        // type 的类型转换
        IceArrayType arrayType = (IceArrayType) (aggregate.getType().getPointTo());
        IceType elementType = arrayType.getElementType();

        ArrayList<IceValue> result = new ArrayList<>();
        // 展开 alloca，并且删除原指令
        for (int i = 0; i < arrayType.getNumElements(); i++) {
            IceAllocaInstruction newAlloca = new IceAllocaInstruction(aggregate.getParent(), aggregate.getName() + "_i" + i, elementType);
            aggregate.getParent().addInstructionAtFront(newAlloca);
            result.add(newAlloca);
        }

        aggregate.getParent().remove(aggregate);

        return result;
    }

    private static void replaceGEPInstructions(IceFunction function, Hashtable<IceAllocaInstruction, ArrayList<IceValue>> allocaLists) {
        // 别名表：IceValue -> 它的别名
        Hashtable<IceValue, IceValue> aliasTable = new Hashtable<>();

        for (IceBlock block : function.getBlocks()) {
            for (int index = 0; index < block.size(); ++index) {
                IceInstruction instr = block.get(index);
                if (instr instanceof IceGEPInstruction gep) {
                    IceValue basePtr = gep.getBasePtr();

                    if (basePtr instanceof IceGlobalVariable || basePtr.getType().isPointer()) {
                        // 如果是全局变量，则不需要处理
                        continue;
                    }

                    if (!(basePtr instanceof IceAllocaInstruction alloca)) {
                        throw new RuntimeException("GEP base pointer is not an alloca instruction");
                    }

                    if (!allocaLists.containsKey(alloca)) {
                        continue;
                    }

                    ArrayList<IceValue> newAllocaList = allocaLists.get(alloca);

                    // TODO: 如果做高维数组展开，这个地方可能要替换
                    // 由于 promotableList 中的 alloca 都只被静态使用，所以可以直接转换
                    IceConstantInt arrayIndex = (IceConstantInt) gep.getOperand(2);
                    IceValue newAlloca = newAllocaList.get((int) arrayIndex.getValue());

                    // gep 相当于设置了一个别名，因此将 gep 的别名设置为 newAlloca
                    aliasTable.put(gep, newAlloca);

                    // 删除 gep
                    block.remove(gep);
                    // 调整 index
                    --index;
                } else {
                    // 如果不是 gep，应当尝试替换指令中的别名
                    List<IceValue> operands = instr.getOperands();
                    for (int i = 0; i < operands.size(); ++i) {
                        IceValue operand = instr.getOperand(i);
                        if (aliasTable.containsKey(operand)) {
                            IceValue newOperand = aliasTable.get(operand);
                            instr.setOperand(i, newOperand);
                        }
                    }
                }
            }
        }
    }

    @Override
    public boolean run(IceFunction target) {
        ArrayList<IceAllocaInstruction> promotableList = createPromotableList(target);
        if (promotableList.isEmpty()) {
            return false;
        }

        Hashtable<IceAllocaInstruction, ArrayList<IceValue>> newAllocaLists = new Hashtable<>();
        for (IceAllocaInstruction alloca : promotableList) {
            ArrayList<IceValue> newAllocaList = aggregatesExpansion(alloca);
            newAllocaLists.put(alloca, newAllocaList);
        }

        replaceGEPInstructions(target, newAllocaLists);
        return true;
    }

    @Override
    public String getName() {
        return "Scalar Replacement Of Aggregates";
    }
}
