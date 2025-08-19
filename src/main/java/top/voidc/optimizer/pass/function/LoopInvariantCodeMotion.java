package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.ds.ChilletGraph;
import top.voidc.misc.ds.DominatorTree;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;

import java.util.*;

@Pass(
        group = {"O0"}
)
public class LoopInvariantCodeMotion implements CompilePass<IceFunction> {

    private static class LoopInfo {
        final IceBlock header;
        final Set<IceBlock> blocks = new HashSet<>();
        final List<IceBlock> latches = new ArrayList<>();

        LoopInfo(IceBlock header) {
            this.header = header;
            blocks.add(header);
        }
    }

    @Override
    public boolean run(IceFunction function) {
        List<LoopInfo> loops = findLoops(function);
        if (loops.isEmpty()) return false;

        boolean changed = false;
        for (LoopInfo loop : loops) {
            changed |= processLoop(loop, function);
        }
        return changed;
    }

    private List<LoopInfo> findLoops(IceFunction function) {
        Map<IceBlock, Set<IceBlock>> dominators = computeDominators(function);
        List<LoopInfo> loops = new ArrayList<>();

        for (IceBlock block : function.getBlocks()) {
            for (IceBlock succ : block.getSuccessors()) {
                // 回边: block -> succ 且 succ 支配 block
                if (dominators.get(block).contains(succ)) {
                    LoopInfo loop = new LoopInfo(succ);
                    loop.latches.add(block);
                    findLoopBlocks(loop, block);
                    loops.add(loop);
                }
            }
        }
        return loops;
    }

    private void findLoopBlocks(LoopInfo loop, IceBlock latch) {
        Stack<IceBlock> stack = new Stack<>();
        stack.push(latch);

        while (!stack.isEmpty()) {
            IceBlock current = stack.pop();
            if (!loop.blocks.add(current)) continue;

            for (IceBlock pred : current.getPredecessors()) {
                if (!loop.blocks.contains(pred)) {
                    stack.push(pred);
                }
            }
        }
    }

    private Map<IceBlock, Set<IceBlock>> computeDominators(IceFunction function) {

        List<IceBlock> blocks = function.getBlocks();

        ChilletGraph<IceBlock> graph = function.getControlFlowGraph();
        // 构建支配树
        DominatorTree<IceBlock> domTree = new DominatorTree<>(graph, graph.getNodeId(function.getEntryBlock()));
        // 构建支配者集合
        Map<IceBlock, Set<IceBlock>> dominators = new HashMap<>();

        for(IceBlock block :blocks)
        {
            dominators.put(block, new HashSet<>());
        }

        // 填充支配者集合
        for(IceBlock block :blocks)
        {
            IceBlock current = block;
            while (current != null) {
                dominators.get(block).add(current);
                // 获取当前块的直接支配者
                IceBlock idom = domTree.getDominator(current);
                current = idom;
            }
        }

        return dominators;
    }

    private boolean processLoop(LoopInfo loop, IceFunction function) {
        IceBlock preheader = createPreheader(loop, function);
        if (preheader == null) return false;

        boolean changed = false;
        boolean moved;

        do {
            moved = false;
            List<IceInstruction> invariants = new ArrayList<>();

            // 遍历循环中的所有指令
            for (IceBlock block : loop.blocks) {
                for (IceInstruction inst : block) {
                    if (inst instanceof IcePHINode || inst.isTerminal()) continue;
                    if (isLoopInvariant(inst, loop)) {
                        invariants.add(inst);
                    }
                }
            }

            // 过滤出本轮可安全外提的指令（无循环内依赖）
            List<IceInstruction> toMove = new ArrayList<>();
            Set<IceInstruction> invariantSet = new HashSet<>(invariants);
            for (IceInstruction inst : invariants) {
                boolean canMove = true;
                for (IceValue operand : inst.getOperands()) {
                    if (operand instanceof IceInstruction opInst) {
                        // 如果操作数在循环内定义且也在本轮收集中，则存在依赖
                        if (loop.blocks.contains(opInst.getParent()) && invariantSet.contains(opInst)) {
                            canMove = false;
                            break;
                        }
                    }
                }
                if (canMove) {
                    toMove.add(inst);
                }
            }

            // 外提指令：批量移动到前置块（保持终结指令在末尾）
            if (!toMove.isEmpty()) {
                IceInstruction term = preheader.getLast();
                // 确保终结指令被正确处理
                if (term != null && term.isTerminal()) {
                    preheader.remove(term);
                    for (IceInstruction inst : toMove) {
                        IceBlock parent = inst.getParent();
                        parent.remove(inst);
                        preheader.addInstruction(inst);
                    }
                    preheader.addInstruction(term);
                } else {
                    // 如果没有终结指令则直接添加
                    for (IceInstruction inst : toMove) {
                        IceBlock parent = inst.getParent();
                        parent.remove(inst);
                        preheader.addInstruction(inst);
                    }
                }
                moved = true;
                changed = true;
            }
        } while (moved);

        return changed;
    }

    private boolean isLoopInvariant(IceInstruction inst, LoopInfo loop) {
        // 检查所有操作数
        for (IceValue operand : inst.getOperands()) {
            if (operand instanceof IceInstruction defInst) {
                // 如果定义在循环内且不是循环不变式
                if (loop.blocks.contains(defInst.getParent()) && !isLoopInvariant(defInst, loop)) {
                    return false;
                }
            } else if (!(operand instanceof IceConstant)) {
                // 不是常量也不是循环不变式
                return false;
            }
        }

        // 检查指令是否有副作用
        return !hasSideEffects(inst);
    }

    private boolean hasSideEffects(IceInstruction inst) {
        return inst instanceof IceStoreInstruction ||
                inst instanceof IceCallInstruction ||
                inst instanceof IceLoadInstruction;
    }

    private IceBlock createPreheader(LoopInfo loop, IceFunction function) {
        IceBlock header = loop.header;
        List<IceBlock> preds = new ArrayList<>(header.getPredecessors());
        preds.removeAll(loop.latches); // 移出回边

        if (preds.size() != 1) {
            // 只处理只有一个前驱的情况
            return null;
        }

        IceBlock originalPred = preds.getFirst();
        if (originalPred.getSuccessors().size() != 1) {
            // 前驱有多个后继，需要创建新的前置块
            IceBlock preheader = new IceBlock(function, "preheader_" + header.getName());
            int index = function.getBlocks().indexOf(header);
            function.getBlocks().add(index, preheader);

            // 重定向分支
            IceInstruction term = originalPred.getLast();
            if (term instanceof IceBranchInstruction br) {

                // 移除原分支指令
                originalPred.remove(term);
                term.destroy();

                if (br.isConditional()) {
                    IceValue condition = br.getCondition();
                    IceBlock trueBlock = br.getTrueBlock();
                    IceBlock falseBlock = br.getFalseBlock();

                    // 替换目标块
                    if (trueBlock == header) {
                        trueBlock = preheader;
                    }
                    if (falseBlock == header) {
                        falseBlock = preheader;
                    }

                    // 创建新的条件分支指令
                    IceBranchInstruction newBr = new IceBranchInstruction(
                            originalPred, condition, trueBlock, falseBlock);
                    originalPred.addInstruction(newBr);
                } else {
                    IceBlock target = br.getTargetBlock();

                    // 替换目标块
                    if (target == header) {
                        target = preheader;
                    }

                    // 创建新的无条件分支指令
                    IceBranchInstruction newBr = new IceBranchInstruction(
                            originalPred, target);
                    originalPred.addInstruction(newBr);
                }
            }

            // 添加前置块到header的跳转
            IceBranchInstruction jump = new IceBranchInstruction(preheader, header);
            preheader.addInstruction(jump);
            return preheader;
        }

        return originalPred;
    }

    @Override
    public String getName() {
        return "Loop Invariant Code Motion";
    }
}