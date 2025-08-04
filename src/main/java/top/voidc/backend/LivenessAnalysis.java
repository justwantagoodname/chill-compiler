package top.voidc.backend;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.ds.BiMap;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 变量活跃性分析 反向数据流分析
 * 使用数据流方程进行迭代
 * out[B] = ∪ in[S]    // S 为 B 的所有后继块
 * in[B]  = use[B] ∪ (out[B] - def[B])
 */
@Pass(group = {"O0", "backend"}, parallel = true)
public class LivenessAnalysis implements CompilePass<IceMachineFunction> {
    private final LivenessResult livenessResult;

    public LivenessAnalysis(IceContext context) {
        this.livenessResult = new LivenessResult();
        context.addPassResult(livenessResult);
    }

    public record BlockLivenessData(Set<IceValue> liveIn, Set<IceValue> liveOut) {}

    public static class LivenessResult {
        private final Map<IceFunction, Map<IceBlock, BlockLivenessData>> livenessData = new ConcurrentHashMap<>();

        public void addLivenessData(IceFunction function, Map<IceBlock, BlockLivenessData> blockLivenessData) {
            livenessData.put(function, blockLivenessData);
        }

        public Map<IceBlock, BlockLivenessData> getLivenessData(IceFunction function) {
            return livenessData.get(function);
        }
    }

    private static class LivenessAnalyzer {
        final BiMap<Integer, IceMachineRegister> registerMapping = new BiMap<>();
        private static class LivenessRecord {
            public BitSet use, def, in, out;

            public LivenessRecord(BitSet use, BitSet def, BitSet in, BitSet out){
                this.use = use;
                this.def = def;
                this.in = in;
                this.out = out;
            }
        }
        final Map<IceBlock, LivenessRecord> blockInfo = new ConcurrentHashMap<>();

        final IceMachineFunction function;
        final List<IceBlock> blocks;

        public LivenessAnalyzer(IceMachineFunction function) {
            this.function = function;
            this.blocks = function.blocks();
            Collections.reverse(this.blocks);
        }

        /**
         * 创建一个id到寄存器的双向映射
         */
        private void createRegisterMapping() {
            var id = new AtomicInteger(0);
            function.getAllRegisters()
                    .forEach(register -> registerMapping.put(id.getAndIncrement(), register));
        }

        private void getUseDef(IceBlock block) {
            var livenessData = blockInfo.computeIfAbsent(block, _ -> {
                var use = new BitSet(registerMapping.size()); // Use 集：在b中使用前未定义新的变量的寄存器
                var def = new BitSet(registerMapping.size()); // Def 集：在b中定义之前没有任何使用的寄存器
                return new LivenessRecord(use, def, new BitSet(registerMapping.size()), new BitSet(registerMapping.size()));
            });
            for (var instruction : block) {
                var machineInstruction = (IceMachineInstruction) instruction;
                for (var operand : machineInstruction.getSourceOperands(true)) {
                    if (operand instanceof IceMachineRegister.RegisterView registerView) {
                        var regId = registerMapping.getKey(registerView.getRegister());
                        assert regId != null;
                        // 如果之前没有重新定义过，那么这个基本块就使用了此寄存器
                        if (!livenessData.def.get(regId)) {
                            livenessData.use.set(regId);
                        }
                    }
                }

                if (machineInstruction.getResultReg(true) != null) {
                    // 有返回值那就是被定义
                    var regId = registerMapping.getKey(machineInstruction.getResultReg(true).getRegister());
                    assert regId != null;
                    // 检查是否之前有使用过 （互斥定义）
                    if (!livenessData.use.get(regId)) {
                        livenessData.def.set(regId);
                    }
                }
            }
        }

        private boolean iterateLiveness() {
            var changed = false;
            for (var block : blocks) {
                var livenessData = blockInfo.get(block);
                assert livenessData != null;

                // 计算 out[B] = ∪ in[S]，S 为 B 的所有后继块
                var out = new BitSet(registerMapping.size());
                for (var successor : block.successors()) {
                    var successorData = blockInfo.get(successor);
                    assert successorData != null;
                    out.or(successorData.in);
                }

                // 计算 in[B] = use[B] ∪ (out[B] - def[B])
                var in = new BitSet(registerMapping.size());
                in.or(livenessData.use);
                var outMinusDef = new BitSet(registerMapping.size());
                outMinusDef.or(out);
                outMinusDef.andNot(livenessData.def);

                in.or(outMinusDef);

                // 检查是否有变化
                if (!livenessData.in.equals(in) || !livenessData.out.equals(out)) {
                    livenessData.in = in;
                    livenessData.out = out;
                    changed = true;
                }
            }
            return changed;
        }

        public void run() {
            createRegisterMapping();
            blocks.parallelStream().forEach(this::getUseDef);

            while (true) {
                // 迭代直到收敛
                if (!iterateLiveness()) break;
            }
        }

        public Map<IceBlock, BlockLivenessData> getLivenessData() {
            Map<IceBlock, BlockLivenessData> result = new HashMap<>();
            for (var entry : blockInfo.entrySet()) {
                var block = entry.getKey();
                var livenessRecord = entry.getValue();

                Set<IceValue> liveIn = new HashSet<>();
                Set<IceValue> liveOut = new HashSet<>();

                for (int i = livenessRecord.in.nextSetBit(0); i >= 0; i = livenessRecord.in.nextSetBit(i + 1)) {
                    liveIn.add(registerMapping.getValue(i));
                }

                for (int i = livenessRecord.out.nextSetBit(0); i >= 0; i = livenessRecord.out.nextSetBit(i + 1)) {
                    liveOut.add(registerMapping.getValue(i));
                }

                result.put(block, new BlockLivenessData(liveIn, liveOut));
            }
            return result;
        }
    }

    @Override
    public boolean run(IceMachineFunction target) {
        var analyzer = new LivenessAnalyzer(target);
        analyzer.run();
        var livenessData = analyzer.getLivenessData();
        this.livenessResult.addLivenessData(target, livenessData);
        for (var entry : livenessData.entrySet()) {
            var block = entry.getKey();
            var data = entry.getValue();
            Log.i("Block " + block.getName() + " Live In: " + data.liveIn + ", Live Out: " + data.liveOut);
        }
        return false;
    }
}
