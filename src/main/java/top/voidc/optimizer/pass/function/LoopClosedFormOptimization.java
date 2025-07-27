package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUser;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.Log;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;

import java.util.*;

@Pass(
        group = {"O0"}
)
public class LoopClosedFormOptimization implements CompilePass<IceFunction> {

    @Override
    public boolean run(IceFunction function) {
        boolean changed = false;

        // 查找所有可能包含循环的基本块
        for (IceBlock block : function.getBlocks()) {
            // 尝试识别并优化简单计数循环
            changed |= optimizeSimpleLoop(block, function);
        }

        return changed;
    }

    private boolean optimizeSimpleLoop(IceBlock header, IceFunction function) {
        // 检查是否满足循环头条件
        if (!isLoopHeader(header)) return false;

        // 获取终止指令（最后一条指令）
        IceInstruction terminator = header.getLast();
        if (!(terminator instanceof IceBranchInstruction branch)) return false;

        // 必须是条件分支
        if (!branch.isConditional()) return false;

        // 确定循环体和退出块
        IceBlock body = null;
        IceBlock exit = null;
        IceBlock latch = null;

        // 获取分支目标
        IceBlock trueBranch = branch.getTrueBlock();
        IceBlock falseBranch = branch.getFalseBlock();

        // 检查哪个后继是回边（即指向循环头）
        if (trueBranch.getSuccessors().contains(header)) {
            body = trueBranch;
            exit = falseBranch;
        } else if (falseBranch.getSuccessors().contains(header)) {
            body = falseBranch;
            exit = trueBranch;
        } else {
            return false; // 没有回边
        }

        // 找到循环回边块（latch）：应该是循环体的最后一个块
        if (body.getSuccessors().contains(header)) {
            latch = body;
        } else {
            return false;
        }
        Log.d("循环回边块为：" + latch.getTextIR());

        // 查找循环PHI节点（归纳变量）
        IcePHINode iv = findInductionVariable(header);
        if (iv == null) return false;
        Log.d("循环PHI节点为：" + iv.getTextIR());

        // 获取初始值和步长
        IceValue initial = null;
        IceValue step = null;

        for (IcePHINode.IcePHIBranch branchInfo : iv.getBranches()) {
            IceBlock source = branchInfo.block();
            IceValue value = branchInfo.value();

            // 初始值分支：来自循环头的前驱（非回边）
            if (source != latch) {
                initial = value;
            } else {
                // 回边分支：来自回边块
                if (value instanceof IceBinaryInstruction.Add add) {
                    IceValue lhs = add.getOperand(0);
                    IceValue rhs = add.getOperand(1);

                    if (lhs == iv && rhs instanceof IceConstantData) {
                        step = rhs;
                    } else if (rhs == iv && lhs instanceof IceConstantData) {
                        step = lhs;
                    }
                }
            }
        }

        if (initial == null || step == null) return false;
        Log.d("初始值为：{ " + initial + " }，步长为：{ " + step + " }");

        // 查找循环条件
        IceCmpInstruction.Icmp cmp = findLoopCondition(header);
        if (cmp == null) return false;
        Log.d("循环条件为：" + cmp.getTextIR());

        // 获取边界值
        IceValue boundary = null;
        boolean isIVOnLeft = false;

        if (cmp.getOperand(0) == iv) {
            boundary = cmp.getOperand(1);
            isIVOnLeft = true;
        } else if (cmp.getOperand(1) == iv) {
            boundary = cmp.getOperand(0);
        } else {
            return false;
        }

        Log.d("边界值为：" + boundary.getTextIR());

        if (!(boundary instanceof IceConstantData)) return false;

        // 计算最终值（闭式形式）
        IceConstantData finalValue = calculateFinalValue(
                (IceConstantData) initial,
                (IceConstantData) step,
                (IceConstantData) boundary,
                cmp.getCmpType(),
                isIVOnLeft
        );

        if (finalValue == null) return false;
        Log.d("最终值为：" + finalValue.getTextIR());

        // 执行优化：替换循环为计算结果
        return replaceLoopWithResult(
                function, header, body, latch, exit, iv, finalValue
        );
    }

    private boolean isLoopHeader(IceBlock block) {
        // 循环头应该有多个前驱
        if (block.getPredecessors().size() < 2) return false;

        // 最后一条指令应该是条件分支
        if (block.isEmpty()) return false;
        IceInstruction terminator = block.getLast();
        if (!(terminator instanceof IceBranchInstruction branch) || !branch.isConditional()) {
            return false;
        }

        // 应该有一个PHI节点
        for (IceInstruction inst : block) {
            if (inst instanceof IcePHINode) {
                return true;
            }
        }
        return false;
    }

    private IcePHINode findInductionVariable(IceBlock header) {
        for (IceInstruction inst : header) {
            if (inst instanceof IcePHINode phi) {
                // 检查是否在回边中被使用
                for (IcePHINode.IcePHIBranch branch : phi.getBranches()) {
                    IceValue value = branch.value();
                    if (value instanceof IceInstruction) {
                        for (IceValue operand : ((IceInstruction) value).getOperands()) {
                            if (operand == phi) {
                                return phi;
                            }
                        }
                    }
                }
            }
        }
        return null;
    }

    private IceCmpInstruction.Icmp findLoopCondition(IceBlock header) {
        // 查找条件分支前的比较指令
        if (header.isEmpty()) return null;
        IceInstruction terminator = header.getLast();

        // 遍历指令查找比较指令
        for (int i = header.size() - 2; i >= 0; i--) {
            IceInstruction inst = header.get(i);
            if (inst instanceof IceCmpInstruction.Icmp cmp) {
                // 检查比较结果是否用于条件分支
                if (terminator.getOperands().contains(cmp)) {
                    return cmp;
                }
            }
        }
        return null;
    }

    private IceConstantData calculateFinalValue(
            IceConstantData initial,
            IceConstantData step,
            IceConstantData boundary,
            IceCmpInstruction.Icmp.Type cmpType,
            boolean isIVOnLeft
    ) {
        // 定义退出条件类型常量
        final int GE = 0; // 大于等于
        final int GT = 1; // 大于
        final int LE = 2; // 小于等于
        final int LT = 3; // 小于



        int exitCmp = -1;
        // need fix
        int initialValue = ((IceConstantInt)initial).getValue();
        int stepValue = ((IceConstantInt)step).getValue();
        int boundaryValue = ((IceConstantInt)boundary).getValue();

        // 根据比较类型和IV位置确定退出条件
        if (isIVOnLeft) {
            switch (cmpType) {
                case SLT: exitCmp = GE; break; // iv < b → 退出条件 iv >= b
                case SLE: exitCmp = GT; break; // iv <= b → 退出条件 iv > b
                case SGT: exitCmp = LE; break; // iv > b → 退出条件 iv <= b
                case SGE: exitCmp = LT; break; // iv >= b → 退出条件 iv < b
                default: return null;
            }
        } else {
            switch (cmpType) {
                case SLT: exitCmp = LE; break; // b < iv → 退出条件 iv <= b
                case SLE: exitCmp = LT; break; // b <= iv → 退出条件 iv < b
                case SGT: exitCmp = GE; break; // b > iv → 退出条件 iv >= b
                case SGE: exitCmp = GT; break; // b >= iv → 退出条件 iv > b
                default: return null;
            }
        }

        // 检查步长方向是否有效
        if ((exitCmp == GE || exitCmp == GT) && stepValue <= 0) {
            return null; // 需要正步长
        }
        if ((exitCmp == LE || exitCmp == LT) && stepValue >= 0) {
            return null; // 需要负步长
        }

        int n = 0; // 迭代次数
        int finalValue; // 最终值

        // 根据退出条件类型计算迭代次数
        switch (exitCmp) {
            case GE: // 退出条件: iv >= boundary
                if (boundaryValue <= initialValue) {
                    n = 0;
                } else {
                    n = (boundaryValue - initialValue + stepValue - 1) / stepValue;
                }
                break;

            case GT: // 退出条件: iv > boundary
                if (boundaryValue < initialValue) {
                    n = 0;
                } else {
                    n = (boundaryValue - initialValue) / stepValue + 1;
                }
                break;

            case LE: // 退出条件: iv <= boundary (步长为负)
                if (initialValue <= boundaryValue) {
                    n = 0;
                } else {
                    int stepAbs = -stepValue; // 步长取绝对值
                    n = (initialValue - boundaryValue + stepAbs - 1) / stepAbs;
                }
                break;

            case LT: // 退出条件: iv < boundary (步长为负)
                if (initialValue < boundaryValue) {
                    n = 0;
                } else if (initialValue == boundaryValue) {
                    n = 1;
                } else {
                    int stepAbs = -stepValue; // 步长取绝对值
                    n = (initialValue - boundaryValue + stepAbs) / stepAbs;
                }
                break;
        }

        // 计算最终值
        finalValue = initialValue + n * stepValue;

        // 创建并返回新的常量
        return IceConstantData.create(finalValue);
    }

    private boolean replaceLoopWithResult(
            IceFunction function,
            IceBlock header,
            IceBlock body,
            IceBlock latch,
            IceBlock exit,
            IcePHINode iv,
            IceConstantData finalValue
    ) {
        // 1. 修改入口块的分支，直接跳转到退出块
        List<IceBlock> predecessors = header.getPredecessors();
        if (predecessors.isEmpty()) return false;

        IceBlock entry = predecessors.getFirst();
        if (entry.isEmpty()) return false;

        // 获取入口块的终止指令
        IceInstruction entryTerminator = entry.getLast();
        if (entryTerminator instanceof IceBranchInstruction) {
            // 创建新的无条件分支到退出块
            IceBranchInstruction newBranch = new IceBranchInstruction(entry, exit);

            // 替换终止指令
            entry.removeLast();
            entry.add(newBranch);
        }


        // 2. 在退出块中替换所有对归纳变量的使用
        for (IceUser user : new ArrayList<>(iv.getUsers())) {
            if (user instanceof IceInstruction instr && instr.getParent().equals(exit)){
                user.replaceOperand(iv, finalValue);
            }
        }

        // 3. 清理循环体
        body.destroy();
        latch.destroy();
        header.destroy();

        return true;
    }

    @Override
    public String getName() {
        return "Loop Closed Form Optimization";
    }
}