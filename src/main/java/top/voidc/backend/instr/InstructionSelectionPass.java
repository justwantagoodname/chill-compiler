package top.voidc.backend.instr;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceUnit;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IcePHINode;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;
import top.voidc.optimizer.pass.DominatorTree;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;

/**
 * 对函数中的每一个基本块进行指令选择
 * 然后替换掉原有指令
 */
@Pass(group = {"O0", "backend"})
public class InstructionSelectionPass implements CompilePass<IceUnit> {
    private final InstructionPack instructionPack;
    private final Collection<InstructionPattern<?>> patternPack;

    public InstructionSelectionPass(InstructionPack instructionPack) {
        this.instructionPack = instructionPack;
        this.patternPack = instructionPack.getPatternPack();
    }

    /**
     * 按照支配树的顺序进行深度优先遍历，并选择指令
     */
    private static class FunctionSelector {
        private final IceFunction function;
        private final IceMachineFunction machineFunction;
        private final HashMap<IceValue, IceMachineValue> valueToMachineValue; // 存放IceValue和寄存器(视图)的关系
        private final DominatorTree<IceBlock> dominatorTree;
        private final Collection<InstructionPattern<?>> patternPack;

        public FunctionSelector(IceFunction function, IceMachineFunction machineFunction, DominatorTree<IceBlock> dominatorTree, Collection<InstructionPattern<?>> patternPack) {
            this.function = function;
            this.machineFunction = machineFunction;
            this.valueToMachineValue = new HashMap<>();
            this.dominatorTree = dominatorTree;
            this.patternPack = patternPack;
            machineFunction.initParameters(valueToMachineValue, function);
        }

        public void dfsSelectBlock(IceBlock currentBlock) {
            var selector = new InstructionSelector(function, machineFunction, valueToMachineValue, currentBlock, this.patternPack);

            // 选择指令
            if (!selector.doSelection()) {
                throw new RuntimeException("Error in selection");
            }

            // 将选择的指令添加到当前块
            var selectedInstructions = selector.getResult();
            var machineBlock = machineFunction.getMachineBlock(currentBlock.getName());
            machineBlock.addAll(selectedInstructions);

            // 遍历当前指令支配的基本块
            for (var dominatee : dominatorTree.getDominatees(currentBlock)) {
                dfsSelectBlock(dominatee);
            }

            // 已经选择完所有支配的块了，清除本次选择中新选择的值
            for (var computedValue : selector.getComputedValues()) {
                if (computedValue instanceof IcePHINode) continue; // 跳过PHI节点，因为它们在SSA中是特殊的前向引用仅用选择一次分配一个虚拟寄存器即可
                valueToMachineValue.remove(computedValue); // 清除本次选择中新选择的值
            }
        }
    }

    @Override
    public boolean run(IceUnit unit) {
        for (var target : List.copyOf(unit.getFunctions())) {
            if (target instanceof IceExternFunction) continue; // 跳过外部函数
            // Phase 1: 对 IceFunction 计算支配树

            var graph = target.getControlFlowGraph();
            var entryNodeId = graph.getNodeId(target.getEntryBlock());
            var dominatorTree = new DominatorTree<>(graph, entryNodeId);

            for (var block : target) {
                Log.d(block + " - " + dominatorTree.getDominatees(block));
            }

            var machineFunction = instructionPack.createMachineFunction(target);

            var funcSelector = new FunctionSelector(target, machineFunction, dominatorTree, patternPack);

            funcSelector.dfsSelectBlock(target.getEntryBlock());

            target.replaceAllUsesWith(machineFunction);
            assert target.getUsers().isEmpty();

            unit.removeFunction(target);
            unit.addFunction(machineFunction);
        }
        return true;
    }
}
