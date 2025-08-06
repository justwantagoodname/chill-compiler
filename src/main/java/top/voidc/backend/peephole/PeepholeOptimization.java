package top.voidc.backend.peephole;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineInstructionComment;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 窥孔优化，注意优化后会丢失注释
 */
@Pass(group = {"O0", "backend"}, parallel = true)
public class PeepholeOptimization implements CompilePass<IceMachineFunction> {

    public static class PeepholeOptimizer {
        private static final List<PeepholePattern> patterns = List.of(
                new RedundantMovePattern(),
                new RedundantLoadStorePattern()
        );

        private final Map<PeepholePattern, Integer> patternsCounter = new HashMap<>();

        private boolean isChanged = false;
        private boolean needRerun = false;

        private final IceMachineFunction machineFunction;
        private List<IceBlock> BBs;

        public PeepholeOptimizer(IceMachineFunction machineFunction) {
            this.machineFunction = machineFunction;
            this.BBs = machineFunction.blocks();
        }

        public boolean isChanged() {
            return isChanged;
        }

        public void run() {
            for (var block : BBs) {
                var filteredInsts = new ArrayList<>(block.stream().filter(inst -> !(inst instanceof IceMachineInstructionComment)).toList());
                for (var startIndex = 0; startIndex < filteredInsts.size(); startIndex++) {
                    for (var pattern : patterns) {
                        var windowSize = pattern.getWindowSize();
                        if (startIndex + windowSize > filteredInsts.size()) continue;

                        var subList = filteredInsts.subList(startIndex, startIndex + windowSize).stream().map(inst -> (IceMachineInstruction) inst).toList();
                        var newInsts = pattern.matchAndApply(subList);
                        if (newInsts != null) {
                            // 替换指令
                            patternsCounter.merge(pattern, 1, Integer::sum);
                            filteredInsts.subList(startIndex, startIndex + windowSize).clear();
                            filteredInsts.addAll(startIndex, newInsts);
                            startIndex += newInsts.size(); // 跳过新指令
                            needRerun = true;
                            break;
                        }
                    }
                }
            }
        }

        public void doOpt() {
            do {
                needRerun = false;
                run();
                isChanged |= needRerun;
            } while (needRerun);
        }

        public void showStats() {
            if (patternsCounter.isEmpty()) {
                return;
            }
            Log.d(machineFunction.getName() + " 窥孔优化统计信息：");
            for (var entry : patternsCounter.entrySet()) {
                Log.d(String.format("\t模式 %s, 使用次数: %d", entry.getKey().getClass().getSimpleName(), entry.getValue()));
            }
        }
    }

    @Override
    public boolean run(IceMachineFunction target) {
        var optimizer = new PeepholeOptimizer(target);
        optimizer.doOpt();
        optimizer.showStats();
        return optimizer.isChanged();
    }
}
