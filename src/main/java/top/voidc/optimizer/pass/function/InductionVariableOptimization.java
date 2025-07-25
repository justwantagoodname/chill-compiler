package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;

import java.util.*;

@Pass(
        group = {"O0", "needfix"}
)
public class InductionVariableOptimization implements CompilePass<IceFunction> {

    private static class IVInfo {
        final IcePHINode phi;
        final IceValue step;
        final IceValue initial;
        final IceInstruction increment;

        IVInfo(IcePHINode phi, IceValue step, IceValue initial, IceInstruction increment) {
            this.phi = phi;
            this.step = step;
            this.initial = initial;
            this.increment = increment;
        }
    }

    @Override
    public boolean run(IceFunction function) {
        boolean changed = false;
        for (IceBlock block : function.getBlocks()) {
            changed |= optimizeBlock(block);
        }
        return changed;
    }

    private boolean optimizeBlock(IceBlock block) {
        List<IVInfo> ivs = findInductionVariables(block);
        if (ivs.isEmpty()) return false;

        boolean changed = false;
        for (IVInfo iv : ivs) {
            changed |= strengthReduction(iv, block);
            changed |= removeUnusedIV(iv);
        }
        return changed;
    }

    private List<IVInfo> findInductionVariables(IceBlock block) {
        List<IVInfo> ivs = new ArrayList<>();

        for (IceInstruction inst : block) {
            if (!(inst instanceof IcePHINode phi)) continue;

            // 检查PHI节点是否符合归纳变量模式
            if (phi.getBranchCount() != 2) continue;

            IceValue initial = null;
            IceValue backedgeValue = null;

            for (int i = 0; i < phi.getBranchCount(); i++) {
                IceValue value = phi.getBranchValueOnIndex(i);
                IceBlock pred = phi.getParent();

                if (pred == block) {
                    // 回边值
                    backedgeValue = value;
                } else {
                    // 初始值
                    initial = value;
                }
            }

            if (initial == null || backedgeValue == null) continue;

            // 检查回边值是否是二元加法操作
            if (backedgeValue instanceof IceBinaryInstruction.Add bin) {
                IceValue lhs = bin.getOperand(0);
                IceValue rhs = bin.getOperand(1);

                if (lhs == phi || rhs == phi) {
                    IceValue step = (lhs == phi) ? rhs : lhs;
                    if (step instanceof IceConstant) {
                        ivs.add(new IVInfo(phi, step, initial, bin));
                    }
                }
            }
        }
        return ivs;
    }

    private boolean strengthReduction(IVInfo iv, IceBlock block) {
        boolean changed = false;

        // 查找使用归纳变量的指令
        for (IceUser user : new ArrayList<>(iv.phi.getUsers())) {
            if (!(user instanceof IceInstruction inst)) continue;

            if (inst.getParent() != block) continue;

            if (inst instanceof IceBinaryInstruction.Mul bin) {
                IceValue lhs = bin.getOperand(0);
                IceValue rhs = bin.getOperand(1);

                if (lhs == iv.phi || rhs == iv.phi) {
                    // 确定乘法的另一个操作数
                    IceValue factor = (lhs == iv.phi) ? rhs : lhs;
                    if (factor instanceof IceConstant) {
                        // 创建新的步长
                        IceValue newStep = multiplyConstants((IceConstant) iv.step, (IceConstant) factor);

                        // 创建新的加法指令
                        IceBinaryInstruction newIncrement = new IceBinaryInstruction.Add(
                                block,
                                iv.phi.getType(),
                                iv.increment,
                                newStep
                        );

                        // 创建新的PHI节点
                        String newPhiName = block.getFunction().generateLocalValueName("iv");
                        IcePHINode newPhi = new IcePHINode(block, newPhiName, iv.phi.getType());

                        // 获取初始块（非回边块）
                        IceBlock initialBlock = null;
                        for (int i = 0; i < iv.phi.getBranchCount(); i++) {
                            if (iv.phi.getParent() != block) {
                                initialBlock = iv.phi.getParent();
                                break;
                            }
                        }

                        if (initialBlock != null) {
                            newPhi.addBranch((IceBlock) iv.initial, initialBlock);
                            newPhi.addBranch(newIncrement.getParent(), block);

                            // 替换原指令
                            bin.replaceAllUsesWith(newPhi);
                            bin.destroy();
                            changed = true;
                        }
                    }
                }
            }
        }
        return changed;
    }

    private IceConstant multiplyConstants(IceConstant a, IceConstant b) {
        // TODO，实际应根据类型处理
        return IceConstantData.create(1);
    }

    private boolean removeUnusedIV(IVInfo iv) {
        boolean changed = false;

        // 检查PHI节点是否未被使用
        if (iv.phi.getUsers().isEmpty()) {
            iv.phi.destroy();
            changed = true;

            // 检查增量指令是否未被使用
            if (iv.increment.getUsers().isEmpty()) {
                iv.increment.destroy();
                changed = true;
            }
        }

        return changed;
    }

    @Override
    public String getName() {
        return "Induction Variable Optimization";
    }
}