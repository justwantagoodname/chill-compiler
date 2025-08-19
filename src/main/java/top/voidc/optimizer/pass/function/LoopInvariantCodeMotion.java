package top.voidc.optimizer.pass.function;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.*;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Qualifier;
import top.voidc.misc.ds.ChilletGraph;
import top.voidc.misc.ds.DominatorTree;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.unit.FunctionPureness;

import java.util.*;

@Pass(
        group = {"O0"}
)
public class LoopInvariantCodeMotion implements CompilePass<IceFunction> {

    private final Map<IceFunction, FunctionPureness.PurenessInfo> functionPurenessInfo;

    public LoopInvariantCodeMotion(@Qualifier("functionPureness") Map<IceFunction, FunctionPureness.PurenessInfo> functionPurenessInfo) {
        // 将函数纯性信息传递给当前优化器
        this.functionPurenessInfo = functionPurenessInfo;

    }

    private static class LoopInfo {
        final IceBlock header;
        final Set<IceBlock> blocks = new HashSet<>();
        final List<IceBlock> latches = new ArrayList<>();
        List<IceBlock> exits;

        LoopInfo(IceBlock header) {
            this.header = header;
            blocks.add(header);
        }

        public List<IceBlock> getExits() {
            exits = new ArrayList<>();
            for (IceBlock block : blocks) {
                for (IceBlock succ : block.successors()) {
                    if (!blocks.contains(succ)) {
                        exits.add(succ);
                        break;
                    }
                }
            }
            return exits;
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
        ChilletGraph<IceBlock> graph = function.getControlFlowGraph();
        DominatorTree<IceBlock> domTree = new DominatorTree<>(graph, graph.getNodeId(function.getEntryBlock()));
        Map<IceBlock, LoopInfo> headerToLoopMap = new HashMap<>();

        for (IceBlock block : function) {
            for (IceBlock succ : block.getSuccessors()) {
                // 回边: block -> succ 且 succ 支配 block
                if (domTree.dominates(succ, block)) {
                    var loop = headerToLoopMap.computeIfAbsent(succ, LoopInfo::new);
                    loop.latches.add(block);
                }
            }
        }

        // 为每个独一无二的循环填充其循环体
        var loops = List.copyOf(headerToLoopMap.values());
        for (var loop : loops) {
            // 现在loop.latches包含了所有回边的源节点
            findLoopBlocks(loop);
        }
        return loops;
    }

    private void findLoopBlocks(LoopInfo loop) {
        // 确保header本身在循环体内
        loop.blocks.add(loop.header);

        Stack<IceBlock> stack = new Stack<>();
        // 将所有的latch块作为遍历的起点
        for (IceBlock latch : loop.latches) {
            stack.push(latch);
        }

        while (!stack.isEmpty()) {
            IceBlock current = stack.pop();
            // 如果块已经处理过，则跳过 (add方法会返回false)
            if (!loop.blocks.add(current)) {
                continue;
            }

            // 遍历前驱
            for (IceBlock pred : current.predecessors()) {
                // 不需要检查 pred 是否已在blocks中，因为下一轮循环的
                // if (!loop.blocks.add(pred)) 会处理重复问题。
                // 实际上，为了防止栈的无意义增长，检查一下更好。
                if (!loop.blocks.contains(pred)) {
                    stack.push(pred);
                }
            }
        }
    }

    private boolean processLoop(LoopInfo loop, IceFunction function) {
        var preHeader = createPreheader(loop, function);
        assert preHeader != null;

        var graph = function.getControlFlowGraph();
        var entryId = graph.getNodeId(function.getEntryBlock());
        var domTree = new DominatorTree<>(graph, entryId);

        boolean changed = false;
        boolean moved;

        do {
            moved = false;
            List<IceInstruction> invariants = new ArrayList<>();

            // 遍历循环中的所有指令
            for (IceBlock block : loop.blocks) {
                for (IceInstruction inst : block) {
                    if (inst instanceof IcePHINode || inst.isTerminal()) continue;
                    if (isLoopInvariant(invariants, inst, loop, domTree)) {
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
                IceInstruction term = preHeader.getLast();
                // 确保终结指令被正确处理
                if (term != null && term.isTerminal()) {
                    preHeader.remove(term);
                    for (IceInstruction inst : toMove) {
                        IceBlock parent = inst.getParent();
                        parent.remove(inst);
                        preHeader.addInstruction(inst);
                    }
                    preHeader.addInstruction(term);
                } else {
                    // 如果没有终结指令则直接添加
                    for (IceInstruction inst : toMove) {
                        IceBlock parent = inst.getParent();
                        parent.remove(inst);
                        preHeader.addInstruction(inst);
                    }
                }
                moved = true;
                changed = true;
            }
        } while (moved);

        return changed;
    }

    private boolean isLoopInvariant(List<IceInstruction> invariant, IceInstruction inst, LoopInfo loop, DominatorTree<IceBlock> domTree) {
        // 检查所有操作数
        for (IceValue operand : inst.getOperands()) {
            if (operand instanceof IceInstruction defInst) {
                // 如果定义在循环内且不是循环不变式
                if (loop.blocks.contains(defInst.getParent()) && !invariant.contains(defInst)) {
                    return false;
                }
            } else if (!(operand instanceof IceConstant)) {
                // 不是常量也不是循环不变式
                return false;
            }
        }

        // 检查指令是否有副作用
        if (hasSideEffects(inst)) return false;

        // 外提指令应该支配所有循环块的出口
        return loop.getExits().stream()
                .allMatch(exit -> domTree.dominates(inst.getParent(), exit));
    }

    private boolean hasSideEffects(IceInstruction inst) {
        if (inst instanceof IceCallInstruction call) {
            return functionPurenessInfo.get(call.getTarget()).getPureness() == FunctionPureness.Pureness.IMPURE;
        }
        return inst instanceof IceStoreInstruction || inst instanceof IceLoadInstruction;
    }

    private IceBlock createPreheader(LoopInfo loop, IceFunction function) {
        IceBlock header = loop.header;
        List<IceBlock> preds = new ArrayList<>(header.getPredecessors());
        preds.removeAll(loop.latches); // 移除回边

        if (preds.size() == 1) {
            // 仅有一个前驱块，直接使用该前驱块作为 preheader
            return preds.getFirst();
        }

        var preHeader = new IceBlock(function, "preheader_" + header.getName());

        // 维护 CFG 结构
        var brInst = new IceBranchInstruction(preHeader, header);
        brInst.setParent(preHeader);
        preHeader.addInstruction(brInst);

        for (var pred : preds) {
            var termInst = pred.getLast();
            termInst.replaceOperand(header, preHeader);
        }

        // 维护 PHI 节点
        var phiList = header.stream().filter(inst -> inst instanceof IcePHINode)
                .map(inst -> (IcePHINode) inst).toList();

        for (var phi : phiList) {
            var outsideMerges = phi.getBranches().stream()
                    .filter(b -> preds.contains(b.block())).toList();

            if (outsideMerges.isEmpty()) continue;

            // 在 preheader 中创建新的 PHI 节点
            var newPhi = new IcePHINode(preHeader, function.generateLocalValueName(), phi.getType());

            newPhi.setValueToBeMerged(phi.getValueToBeMerged());

            for (var branch : outsideMerges) {
                // 将外部分支添加到新的 PHI 节点
                newPhi.addBranch(branch.block(), branch.value());
            }

            // 将新的 PHI 节点添加到 preheader
            preHeader.addFirst(newPhi);

            // 删除原 PHI 节点中的外部分支
            for (var branch : outsideMerges) {
                phi.removeValueByBranch(branch.block());
            }

            // 如果原 PHI 节点没有分支了，则将其替换为 preheader 中的 PHI 节点
            if (phi.getBranches().isEmpty()) {
                phi.replaceAllUsesWith(newPhi);
                phi.destroy();
            } else {
                // 将 preheader 中的 PHI 节点添加到原 PHI 节点的 branch 中
                phi.addBranch(preHeader, newPhi);
            }
        }

        return preHeader;
    }

    @Override
    public String getName() {
        return "Loop Invariant Code Motion";
    }
}