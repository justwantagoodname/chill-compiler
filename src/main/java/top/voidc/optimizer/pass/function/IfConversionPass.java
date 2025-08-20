package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

@Pass
public class IfConversionPass implements CompilePass<IceFunction> {
    @Override
    public boolean run(IceFunction target) {
        boolean flag = false;

        for(var block : target){
            // 查找phi
            var phi = getConversionCandidate(block);
            if(phi == null) continue;

            // TODO 优化本体
            flag = true;
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

            // 检查指令是否过于复杂（根据成本模型）
            if (isTooExpensive(instr)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSideEffects(IceInstruction instr) {
        return switch (instr) {
            case IceStoreInstruction store -> true;
            case IceCallInstruction call -> true;
            default -> false;
        };
    }

    private boolean isTooExpensive(IceInstruction instr) {
        // TODO不会，先统统返回false
        return false;
    }

    @Override
    public String getName(){
        return "IfConversion";
    }
}
