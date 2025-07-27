package top.voidc.backend.regallocator;

import top.voidc.backend.LivenessAnalysis;
import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;

import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class LinearScanAllocator implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    private final LivenessAnalysis.LivenessResult livenessResult;
    private final IceContext iceContext;
    private List<IceBlock> BBs;
    private Map<IceBlock, LivenessAnalysis.BlockLivenessData> functionLivenessData;
    private Map<IceMachineRegister, IceStackSlot> vregSlotMap = new HashMap<>();

    private static class LiveInterval {
        IceMachineRegister vreg, preg;
        int start, end;

        public LiveInterval(IceMachineRegister vreg, int start, int end) {
            this.vreg = vreg;
            this.start = start;
            this.end = end;
        }
    }


    public LinearScanAllocator(IceContext context, LivenessAnalysis.LivenessResult livenessResult) {
        this.livenessResult = livenessResult;
        this.iceContext = context;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        if (!(target instanceof ARM64Function mf)) {
            throw new IllegalArgumentException("LinearScanAllocator only supports ARM64Function.");
        }

        functionLivenessData = livenessResult.getLivenessData(target);

        this.BBs = target.getBlocks();

        var intervals = buildLiveIntervals(mf);
        intervals.sort(Comparator.comparingInt(i -> i.start));

        linearScan(mf, intervals);

        applyRegisterAllocation(mf, intervals);

        return true;
    }

    private List<LiveInterval> buildLiveIntervals(ARM64Function mf) {
        Map<IceMachineRegister, LiveInterval> intervalMap = new HashMap<>();
        Map<IceBlock, Integer> blockEndIndex = new HashMap<>();

        int currentIndex = 0;
        for (var block : BBs) {
            // 左闭右闭区间
            blockEndIndex.put(block, currentIndex + block.size() - 1);

            for (var instruction : block) {
                if (!(instruction instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function?");
                }
                for (IceValue operand : inst.getSourceOperands()) {
                    if (operand instanceof IceMachineRegister.RegisterView rv) {
                        IceMachineRegister reg = rv.getRegister();
                        if (reg.isVirtualize()) {
                            intervalMap.putIfAbsent(reg, new LiveInterval(reg, currentIndex, currentIndex));
                        }
                    }
                }

                IceMachineRegister.RegisterView rv = inst.getResultReg();
                if (rv != null && rv.getRegister().isVirtualize()) {
                    IceMachineRegister reg = rv.getRegister();
                    intervalMap.putIfAbsent(reg, new LiveInterval(reg, currentIndex, currentIndex));
                }

                ++currentIndex;
            }
        }

        for (var block : BBs) {
            var liveOut = functionLivenessData.get(block).liveOut();
            for (IceValue value : liveOut) {
                if (value instanceof IceMachineRegister.RegisterView rv) {
                    IceMachineRegister reg = rv.getRegister();
                    if (reg.isVirtualize()) {
                        LiveInterval interval = intervalMap.get(reg);
                        if (interval != null) {
                            interval.end = Math.max(interval.end, blockEndIndex.get(block)); // 更新结束位置
                        }
                    }
                }
            }
        }

        return new ArrayList<>(intervalMap.values());
    }

    private void linearScan(ARM64Function mf, List<LiveInterval> intervals) {
        List<LiveInterval> active = new ArrayList<>();
        List<IceMachineRegister> freeRegisters = getPhysicalRegisters(mf);

        for (LiveInterval interval : intervals) {
            expireOldIntervals(interval, active, freeRegisters);

            if (freeRegisters.isEmpty()) {
                // 没有可用寄存器，选择一个溢出
                spillAtInterval(interval, active, freeRegisters);
            }

            // 分配一个可用的
            interval.preg = freeRegisters.removeFirst();

            active.add(interval); // 将当前区间添加到活跃区间列表
            active.sort(Comparator.comparingInt(i -> i.end));
        }

    }

    private List<IceMachineRegister> getPhysicalRegisters(ARM64Function mf) {
        ArrayList<IceMachineRegister> regPool = new ArrayList<>();

        for (int i = 9; i <= 15; ++i) {
            regPool.add(mf.getPhysicalRegister("x" + i));
        }

        return regPool;
    }

    private void expireOldIntervals(LiveInterval current, List<LiveInterval> active, List<IceMachineRegister> freeRegisters) {
        // 过期的区间是那些结束位置小于当前区间开始位置的区间
        active.removeIf(interval -> {
            if (interval.end >= current.start) {
                return false; // 这个区间还活跃
            }
            freeRegisters.add(interval.preg); // 将物理寄存器释放回寄存器池
            return true; // 这个区间过期了
        });
    }

    private void spillAtInterval(LiveInterval current, List<LiveInterval> active, List<IceMachineRegister> freeRegisters) {
        // 选择一个活跃区间进行溢出
        if (active.isEmpty()) {
            throw new IllegalStateException("No active intervals to spill.");
        }

        // 找到结束位置最早的区间进行溢出
        LiveInterval spillInterval = active.stream()
                .min(Comparator.comparingInt(i -> i.end))
                .orElseThrow(() -> new IllegalStateException("No active intervals found."));

        // 将其物理寄存器释放回寄存器池
        freeRegisters.add(spillInterval.preg);
        spillInterval.preg = null; // 清除物理寄存器引用
    }

    private void applyRegisterAllocation(ARM64Function mf, List<LiveInterval> intervals) {
        Map<IceMachineRegister, IceMachineRegister> regMapping = new HashMap<>();
        for (var interval : intervals) {
            if (interval.preg != null) {
                // 将虚拟寄存器映射到物理寄存器
                regMapping.put(interval.vreg, interval.preg);
            } else {
                vregSlotMap.putIfAbsent(interval.vreg, mf.allocateVariableStackSlot(interval.vreg.getType()));
            }
        }

        for (var block : BBs) {
            for (var instruction : block) {
                if (!(instruction instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function???");
                }

                replaceRegisters(mf, inst, regMapping);
            }
        }

        insertSpillCode(mf);
    }

    private void replaceRegisters(ARM64Function mf, IceMachineInstruction inst, Map<IceMachineRegister, IceMachineRegister> regMapping) {
        for (int i = 0; i < inst.getOperands().size(); i++) {
            IceValue operand = inst.getOperand(i);
            if (operand instanceof IceMachineRegister.RegisterView rv) {
                IceMachineRegister reg = rv.getRegister();
                if (reg.isVirtualize()) {
                    // 如果是虚拟寄存器，替换为物理寄存器或栈槽
                    IceMachineRegister mappedReg = regMapping.get(reg);
                    if (mappedReg != null) {
                        inst.replaceOperand(operand, mappedReg.createView(rv.getType()));
                    } else {
                        IceStackSlot slot = vregSlotMap.get(reg);
                        inst.replaceOperand(operand, slot);
                    }
                }
            }
        }
    }

    private void insertSpillCode(ARM64Function mf) {
        List<IceMachineInstruction> newInstructions = new ArrayList<>();

        IceMachineRegister tempReg = mf.getPhysicalRegister("x8");

        for (var block : BBs) {
            List<IceMachineInstruction> loadInstructions = new ArrayList<>();


            for (int i = 0; i < block.size(); ++i) {
                if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function?????");
                }

                Map<IceValue, IceValue> replacements = new HashMap<>();

                for (IceValue operand : inst.getSourceOperands()) {
                    if (operand instanceof IceStackSlot slot) {
                        // 如果是栈槽，需要替换
                        IceMachineInstruction loadInst = new ARM64Instruction("LDR {dst}, {local:src}",
                                tempReg.createView(operand.getType()), slot);
                        loadInst.setParent(block);
                        block.add(i, loadInst);
                        i++; // 插入后需要更新索引
                        loadInstructions.add(loadInst);
                        replacements.put(operand, loadInst.getResultReg());
                    }
                }

                for (var entry : replacements.entrySet()) {
                    inst.replaceOperand(entry.getKey(), entry.getValue());
                }

                IceMachineRegister.RegisterView resultReg = inst.getResultReg();
                if (resultReg != null && vregSlotMap.containsKey(resultReg.getRegister())) {
                    IceStackSlot slot = vregSlotMap.get(resultReg.getRegister());

                    // 创建存储指令
                    IceMachineInstruction storeInst = new ARM64Instruction("STR {src}, {local:dst}",
                            resultReg, slot);
                    storeInst.setParent(block);
                    block.add(i, storeInst);
                    i++; // 插入后需要更新索引
                }
            }


        }
    }

    @Override
    public String getArchitecture() {
        return "armv8-a";
    }

    @Override
    public String getABIName() {
        return "linux-gnu-glibc";
    }

    @Override
    public int getBitSize() {
        return 64;
    }
}
