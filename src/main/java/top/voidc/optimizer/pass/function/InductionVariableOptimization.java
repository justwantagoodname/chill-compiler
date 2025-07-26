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
        group = {"O0"}
)
public class InductionVariableOptimization implements CompilePass<IceFunction> {

    private static class IVInfo {
        final IcePHINode phi;
        final IceValue step;
        final IceValue initial;
        final IceInstruction increment;
        final IceBlock initialBlock;
        final IceBlock backedgeBlock;
        final boolean isBasic; // 标记是否为基本归纳变量
        IceValue baseIV; // 所基于的基本归纳变量
        IceConstantData coefficient; // 相对于基本归纳变量的系数
        IceConstantData offset; // 相对于基本归纳变量的偏移

        // 基本归纳变量的构造方法
        IVInfo(IcePHINode phi, IceValue step, IceValue initial,
               IceInstruction increment, IceBlock initialBlock, IceBlock backedgeBlock) {
            this.phi = phi;
            this.step = step;
            this.initial = initial;
            this.increment = increment;
            this.initialBlock = initialBlock;
            this.backedgeBlock = backedgeBlock;
            this.isBasic = true;
            this.baseIV = phi; // 基本归纳变量基于自身
            this.coefficient = IceConstantData.create(1); // 系数为1
            this.offset = IceConstantData.create(0); // 偏移为0
        }

        // 派生归纳变量的构造方法
        IVInfo(IcePHINode phi, IceValue step, IceValue initial,
               IceInstruction increment, IceBlock initialBlock, IceBlock backedgeBlock,
               IceValue baseIV, IceConstantData coefficient, IceConstantData offset) {
            this.phi = phi;
            this.step = step;
            this.initial = initial;
            this.increment = increment;
            this.initialBlock = initialBlock;
            this.backedgeBlock = backedgeBlock;
            this.isBasic = false;
            this.baseIV = baseIV;
            this.coefficient = coefficient;
            this.offset = offset;
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
        Queue<IVInfo> queue = new LinkedList<>(ivs);

        while (!queue.isEmpty()) {
            IVInfo iv = queue.poll();

            // 强度削弱可能产生新的派生归纳变量
            List<IVInfo> newIVs = strengthReduction(iv, block);
            queue.addAll(newIVs);

            // 线性函数测试替换
            changed |= linearFunctionTestReplacement(iv, block);

            // 删除无用归纳变量
            changed |= removeUnusedIV(iv);
        }
        return changed;
    }

    private List<IVInfo> findInductionVariables(IceBlock block) {
        List<IVInfo> ivs = new ArrayList<>();

        for (IceInstruction inst : block) {
            if (!(inst instanceof IcePHINode phi)) continue;
            if (phi.getBranchCount() < 2) continue;

            IceValue initial = null;
            IceValue backedgeValue = null;
            IceBlock initialBlock = null;
            IceBlock backedgeBlock = null;

            List<IcePHINode.IcePHIBranch> branches = phi.getBranches();
            for (IcePHINode.IcePHIBranch branch : branches) {
                IceValue value = branch.value();
                IceBlock sourceBlock = branch.block();

                // 检查值是否引用PHI节点（回边分支特征）
                boolean isBackedge = false;
                if (value instanceof IceInstruction) {
                    for (IceValue operand : ((IceInstruction) value).getOperands()) {
                        if (operand == phi) {
                            isBackedge = true;
                            break;
                        }
                    }
                }

                if (isBackedge) {
                    backedgeValue = value;
                    backedgeBlock = sourceBlock;
                } else if (initial == null) { // 只取第一个非回边分支作为初始值
                    initial = value;
                    initialBlock = sourceBlock;
                }
            }

            if (initial == null || backedgeValue == null) continue;

            // 检查回边值是否是加法指令
            if (backedgeValue instanceof IceBinaryInstruction.Add add) {
                IceValue lhs = add.getOperand(0);
                IceValue rhs = add.getOperand(1);
                IceValue step = null;

                if (lhs == phi) step = rhs;
                else if (rhs == phi) step = lhs;

                if (step instanceof IceConstant) {
                    ivs.add(new IVInfo(phi, step, initial, add, initialBlock, backedgeBlock));
                }
            }
        }
        return ivs;
    }

    private List<IVInfo> strengthReduction(IVInfo iv, IceBlock block) {
        List<IVInfo> newIVs = new ArrayList<>();
        List<IceUser> users = new ArrayList<>(iv.phi.getUsers());

        for (IceUser user : users) {
            if (!(user instanceof IceInstruction inst)) continue;
            if (inst.getParent() != block) continue;

            if (inst instanceof IceBinaryInstruction.Mul mul) {
                IceValue lhs = mul.getOperand(0);
                IceValue rhs = mul.getOperand(1);
                IceValue factor = null;

                if (lhs == iv.phi) factor = rhs;
                else if (rhs == iv.phi) factor = lhs;

                if (factor instanceof IceConstantData) {
                    // 计算新步长 = 原步长 * 因子
                    IceConstantData newStepVal = multiplyConstants(
                            (IceConstantData) iv.step,
                            (IceConstantData) factor
                    );

                    // 计算初始值 = 原初始值 * 因子
                    IceConstantData initVal = multiplyConstants(
                            (IceConstantData) iv.initial,
                            (IceConstantData) factor
                    );

                    // 创建新的PHI节点
                    String newPhiName = block.getFunction().generateLocalValueName("iv.sr");
                    IcePHINode newPhi = new IcePHINode(block, newPhiName, mul.getType());

                    // 在回边块创建新的加法指令
                    IceBinaryInstruction newAdd = new IceBinaryInstruction.Add(
                            iv.backedgeBlock,
                            mul.getType(),
                            newPhi,
                            newStepVal
                    );
                    iv.backedgeBlock.addInstruction(newAdd);

                    // 设置新PHI节点的分支
                    newPhi.addBranch(iv.initialBlock, initVal);
                    newPhi.addBranch(iv.backedgeBlock, newAdd);

                    // 计算派生归纳变量的系数和偏移
                    IceConstantData newCoefficient = multiplyConstants(iv.coefficient, (IceConstantData) factor);
                    IceConstantData newOffset = multiplyConstants(iv.offset, (IceConstantData) factor);

                    // 创建派生归纳变量信息
                    IVInfo derivedIV = new IVInfo(newPhi, newStepVal, initVal, newAdd,
                            iv.initialBlock, iv.backedgeBlock,
                            iv.baseIV, newCoefficient, newOffset);
                    newIVs.add(derivedIV);

                    // 替换原指令并删除
                    mul.replaceAllUsesWith(newPhi);
                    mul.destroy();
                }
            }
        }
        return newIVs;
    }

    private boolean linearFunctionTestReplacement(IVInfo iv, IceBlock block) {
        if (iv.isBasic) {
            return false; // 基本归纳变量不需要LFTR
        }

        boolean changed = false;
        List<IceUser> users = new ArrayList<>(iv.phi.getUsers());

        for (IceUser user : users) {
            if (!(user instanceof IceCmpInstruction.Icmp cmp)) continue;
            if (cmp.getParent() != block) continue;

            IceValue op0 = cmp.getOperand(0);
            IceValue op1 = cmp.getOperand(1);
            boolean cmpAgainstIV = false;
            IceValue boundary = null;
            boolean isIVOnLeft = false;
            IceCmpInstruction.Icmp.Type cmpType = cmp.getCmpType();

            if (op0 == iv.phi) {
                boundary = op1;
                cmpAgainstIV = true;
                isIVOnLeft = true;
            } else if (op1 == iv.phi) {
                boundary = op0;
                cmpAgainstIV = true;
            }

            if (cmpAgainstIV && boundary instanceof IceConstantData boundaryConst) {
                // 计算新的边界值: (boundary - offset) / coefficient

                // 减去偏移量
                IceConstantData adjustedBoundary = boundaryConst.minus(iv.offset);

                // 除以系数
                IceConstantData newBoundary = adjustedBoundary.divide(iv.coefficient);

                // 创建新的比较指令
                IceCmpInstruction.Icmp newCmp;
                if (isIVOnLeft) {
                    // 原比较: iv.phi < boundary 转换为: baseIV < newBoundary
                    newCmp = new IceCmpInstruction.Icmp(
                            block,
                            cmpType,
                            iv.baseIV,
                            newBoundary
                    );
                } else {
                    // 原比较: boundary < iv.phi 转换为: newBoundary < baseIV
                    // 注意：需要反转比较类型
                    IceCmpInstruction.Icmp.Type reversedType = reverseCmpType(cmpType);
                    newCmp = new IceCmpInstruction.Icmp(
                            block,
                            reversedType,
                            newBoundary,
                            iv.baseIV
                    );
                }

                // 将新指令插入到基本块中
                block.addInstructionAfter(newCmp, cmp);

                // 替换原比较指令
                cmp.replaceAllUsesWith(newCmp);
                cmp.destroy();
                changed = true;
            }
        }
        return changed;
    }

    private IceCmpInstruction.Icmp.Type reverseCmpType(IceCmpInstruction.Icmp.Type type) {
        return switch (type) {
            case SLT -> IceCmpInstruction.Icmp.Type.SGT;
            case SLE -> IceCmpInstruction.Icmp.Type.SGE;
            case SGT -> IceCmpInstruction.Icmp.Type.SLT;
            case SGE -> IceCmpInstruction.Icmp.Type.SLE;
            case EQ -> type; // EQ反转后还是EQ
            case NE -> type; // NE反转后还是NE
        };
    }

    private boolean removeUnusedIV(IVInfo iv) {
        boolean changed = false;

        // 删除未被使用的PHI节点
        if (iv.phi.getUsers().isEmpty()) {
            iv.phi.destroy();
            changed = true;
        }

        // 删除未被使用的增量指令
        if (iv.increment != null && iv.increment.getUsers().isEmpty()) {
            iv.increment.destroy();
            changed = true;
        }

        return changed;
    }

    private IceConstantData multiplyConstants(IceConstantData a, IceConstantData b) {
        return a.multiply(b);
    }

    @Override
    public String getName() {
        return "Induction Variable Optimization";
    }
}