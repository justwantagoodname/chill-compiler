package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.HashSet;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Pass
public class IfConversionPass implements CompilePass<IceFunction> {
    @Override
    public boolean run(IceFunction target) {
        boolean flag = false;

        // 复制基本块列表，防止遍历时的并发修改
        var blocks = List.copyOf(target.blocks());

        var removedBlocks = new HashSet<IceBlock>();

        for(var block : blocks){
            // 跳过已被删除的基本块
            if (removedBlocks.contains(block)) {
                continue;
            }

            // 查找phi
            var phi = getConversionCandidate(block);
            if(phi == null) continue;

            // 获取条件分支指令和merge块
            var brInstr = (IceBranchInstruction) block.getLast();
            var condition = brInstr.getCondition();
            var trueBr = brInstr.getTrueBlock();
            var falseBr = brInstr.getFalseBlock();
            var mergeBlock = trueBr.getSuccessors().getFirst();
            
            // 移动分支块中的非终结指令到select之前
            AtomicInteger insertIndex = new AtomicInteger(block.size() - 1); // 在br指令之前插入
            trueBr.safeForEach(inst -> {
                if (!inst.isTerminal()) {
                    inst.moveTo(block, insertIndex.getAndIncrement());
                }
            });
            falseBr.safeForEach(inst -> {
                if (!inst.isTerminal()) {
                    inst.moveTo(block, insertIndex.getAndIncrement());
                }
            });

            // 获取true和false值
            var trueValue = phi.getIncomingValue(trueBr);
            var falseValue = phi.getIncomingValue(falseBr);

            // 创建select指令并插入到block中
            var select = new IceSelectInstruction(block, condition, trueValue, falseValue);
            block.add(select);
            
            // 替换phi的所有使用为select
            phi.replaceAllUsesWith(select);
            
            // 清理phi节点
            phi.destroy();

            // 删除条件分支
            brInstr.destroy();
            
            // 创建并添加无条件跳转到merge块
            var newBr = new IceBranchInstruction(block, mergeBlock);
            block.add(newBr);
            
            // 清理不再需要的分支块
            trueBr.destroy();
            falseBr.destroy();
            removedBlocks.add(trueBr);
            removedBlocks.add(falseBr);

            flag = true;
        }


        if (!target.getReturnType().isVoid()) {
            // 处理返回值的优化
            blocks = List.copyOf(target.blocks());
            removedBlocks.clear();
            for (var block : blocks) {
                // 跳过已删除的基本块
                if (removedBlocks.contains(block)) {
                    continue;
                }

                // 检查是否是条件分支
                var brInstr = block.getLast();
                if (!(brInstr instanceof IceBranchInstruction br) || !br.isConditional()) {
                    continue;
                }

                // 获取分支块
                var trueBr = br.getTrueBlock();
                var falseBr = br.getFalseBlock();

                // 检查单一前驱
                if (trueBr.getPredecessors().size() != 1 || falseBr.getPredecessors().size() != 1) {
                    continue;
                }

                // 检查返回指令
                var trueRet = trueBr.getLast();
                var falseRet = falseBr.getLast();
                if (!(trueRet instanceof IceRetInstruction) || !(falseRet instanceof IceRetInstruction)) {
                    continue;
                }

                var trueRetInstr = (IceRetInstruction) trueRet;
                var falseRetInstr = (IceRetInstruction) falseRet;

                // 确保两个分支都有返回值
                if (trueRetInstr.isReturnVoid() || falseRetInstr.isReturnVoid()) {
                    continue;
                }

                // 检查两个分支块是否适合转换
                if (isNotSafeToConvert(trueBr) || isNotSafeToConvert(falseBr)) {
                    continue;
                }

                // 检查转换成本是否太高
                if (isTooExpensive(trueBr, falseBr)) {
                    continue;
                }

                // 移动分支块中的非终结指令到select之前
                AtomicInteger insertIndex = new AtomicInteger(block.size() - 1); // 在br指令之前插入
                trueBr.safeForEach(inst -> {
                    if (!inst.isTerminal()) {
                        inst.moveTo(block, insertIndex.getAndIncrement());
                    }
                });

                falseBr.safeForEach(instr -> {
                    if (!instr.isTerminal()) {
                        instr.moveTo(block, insertIndex.getAndIncrement());
                    }
                });

                // 获取返回值
                var trueVal = trueRetInstr.getReturnValue().get();
                var falseVal = falseRetInstr.getReturnValue().get();

                // 创建select指令
                var select = new IceSelectInstruction(block, br.getCondition(), trueVal, falseVal);
                block.add(select);

                // 创建返回指令
                var ret = new IceRetInstruction(block, select);
                block.add(ret);

                // 删除原条件分支
                brInstr.destroy();

                // 删除不需要的分支块
                trueBr.destroy();
                falseBr.destroy();
                removedBlocks.add(trueBr);
                removedBlocks.add(falseBr);

                flag = true;
            }
        }

        return flag;
    }

    // 获取需要优化的phi
    private IcePHINode getConversionCandidate(IceBlock block){
        // 查询终止指令是否为条件分支
        var ter = block.getLast();
        if(!(ter instanceof IceBranchInstruction brInstr) || ! brInstr.isConditional()) {
            return null;
        }

        // 获取两个后继块
        var trueBr = brInstr.getTrueBlock();
        var falseBr = brInstr.getFalseBlock();

        // 检查两个后继块是否都只有一个前驱
        if (trueBr.getPredecessors().size() != 1 || falseBr.getPredecessors().size() != 1) {
            return null;
        }

        if(trueBr.getSuccessors().size() != 1 || falseBr.getSuccessors().size() != 1) {
            return null;
        }

        // 检查两个后继块是否都流向同一个合并块
        var trueSucc = trueBr.getSuccessors().getFirst();
        var falseSucc = falseBr.getSuccessors().getFirst();

        if (trueSucc.getFirst() == null || trueSucc != falseSucc) {
            return null;
        }

        // 检查两个分支块是否适合转换
        if (isNotSafeToConvert(trueBr) || isNotSafeToConvert(falseBr)) {
            return null;
        }

        // 检查转换成本是否太高
        if (isTooExpensive(trueBr, falseBr)) {
            return null;
        }

        // 查找唯一PHI节点（多个PHI节点返回null）
        return getConvertiblePHI(trueSucc, trueBr, falseBr);
    }

    private IcePHINode getConvertiblePHI(IceBlock merge, IceBlock trueBr, IceBlock falseBr) {
        IcePHINode ans = null;

        // 遍历整个merge查找phi
        for(var instr : merge){
            if(!(instr instanceof IcePHINode phi)) continue;

            var trueValue = phi.getIncomingValue(trueBr);
            var falseValue = phi.getIncomingValue(falseBr);

            if (trueValue != null && falseValue != null
            && trueValue.getType() == falseValue.getType()) {
                if(ans != null){
                    return null;
                }
                ans = phi;
            }
        }
        return ans;
    }

    /**
     * 检查基本块是否适合转换（无副作用、简单指令等）
     */
    private boolean isNotSafeToConvert(IceBlock block) {
        for (var instr : block) {
            // 跳过终止指令
            if (instr.isTerminal()) continue;

            // 检查指令是否有副作用
            if (hasSideEffects(instr)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSideEffects(IceInstruction instr) {
        return switch (instr) {
            case IceStoreInstruction _, IceCallInstruction _ -> true;
            default -> false;
        };
    }

    /**
     * 检查转换的成本是否太高
     * @param trueBr true分支块
     * @param falseBr false分支块
     * @return 如果成本太高返回true
     */
    private static boolean isTooExpensive(IceBlock trueBr, IceBlock falseBr) {
        // 统计两个分支块的非终结指令数量
        int trueCount = countNonTerminalInstructions(trueBr);
        int falseCount = countNonTerminalInstructions(falseBr);
        
        // 如果任一分支的指令数量过多，则认为转换成本太高
        // 设置阈值为5条指令
        return trueCount > 5 || falseCount > 5;
    }

    /**
     * 统计基本块中的非终结指令数量
     * @param block 要统计的基本块
     * @return 非终结指令的数量
     */
    private static int countNonTerminalInstructions(IceBlock block) {
        int count = 0;
        for (var instr : block) {
            if (!instr.isTerminal()) {
                count++;
            }
        }
        return count;
    }
}
