package top.voidc.backend.regallocator;

import top.voidc.backend.LivenessAnalysis;
import top.voidc.backend.arm64.instr.ARM64Function;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.*;

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
    private final Map<IceMachineRegister, IceStackSlot> vregSlotMap = new HashMap<>();
    private final Map<IceStackSlot, IceMachineRegister> slotVregMap = new HashMap<>();

    // preg -> vreg 记录目前某个物理寄存器上占用的虚拟寄存器
    private Map<IceMachineRegister, IceMachineRegister> vregOnPreg = new HashMap<>();

    private static class LiveInterval {
        IceMachineRegister vreg, preg;
        int start, end;
        ArrayList<Integer> uses = new ArrayList<>(); // 记录使用位置

        public LiveInterval(IceMachineRegister vreg, int start, int end) {
            this.vreg = vreg;
            this.start = start;
            this.end = end;
        }

        public int nextUseAfter(int pos) {
            for (int use : uses) {
                if (use > pos) {
                    return use;
                }
            }
            return Integer.MAX_VALUE; // 如果没有找到，返回一个很大的值
        }

        public LiveInterval splitAt(int pos) {
            if (pos <= start || pos >= end) {
                throw new IllegalArgumentException("Cannot split interval at position: " + pos);
            }
            LiveInterval newInterval = new LiveInterval(vreg, pos, end);
            this.end = pos; // 更新当前区间的结束位置
            return newInterval; // 返回新的区间
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

        this.BBs = target.getBFSBlocks();

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
            // 左闭右开区间
            blockEndIndex.put(block, currentIndex + block.size());

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
                    intervalMap.putIfAbsent(reg, new LiveInterval(reg, currentIndex, currentIndex + 1));
                    intervalMap.get(reg).uses.add(currentIndex); // 记录使用位置
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

        LiveInterval spillInterval = current;
        LiveInterval old = active.getLast();
        if (old.end > current.end) {
            spillInterval = old;
        }

        int spillPoint = spillInterval.nextUseAfter(current.start);

        active.remove(spillInterval);
        var newInterval = spillInterval.splitAt(spillPoint);
        active.add(newInterval);

        // 将其物理寄存器释放回寄存器池
        freeRegisters.add(newInterval.preg);
        newInterval.preg = null; // 清除物理寄存器引用
    }

    private void applyRegisterAllocation(ARM64Function mf, List<LiveInterval> intervals) {
        Map<IceMachineRegister, IceMachineRegister> regMapping = new HashMap<>();
        for (var interval : intervals) {
            if (interval.preg != null) {
                // 将虚拟寄存器映射到物理寄存器
                regMapping.put(interval.vreg, interval.preg);
            } else {
                var slot = mf.allocateVariableStackSlot(interval.vreg.getType());
                vregSlotMap.putIfAbsent(interval.vreg, slot);
                slotVregMap.putIfAbsent(slot, interval.vreg);
            }
        }

        for (var block : BBs) {
            for (int i = 0; i < block.size(); ++i) {
                if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function???");
                }

                replaceRegisters(block, i, inst, regMapping);
            }
        }

        insertSpillCode(mf);
    }

    /**
     * 替换指令中的寄存器，必要时插入溢出代码
     * @param block
     * @param inst
     * @param regMapping
     * @return 插入了多少条溢出代码
     */
    private int replaceRegisters(IceBlock block, int pos, IceMachineInstruction inst, Map<IceMachineRegister, IceMachineRegister> regMapping) {
        int result = 0;
        for (int i = 0; i < inst.getOperands().size(); i++) {
            IceValue operand = inst.getOperand(i);
            if (operand instanceof IceMachineRegister.RegisterView rv) {
                IceMachineRegister reg = rv.getRegister();
                if (reg.isVirtualize()) {
                    // 如果是虚拟寄存器，替换为物理寄存器或栈槽
                    IceMachineRegister mappedReg = regMapping.get(reg);
                    if (mappedReg != null) {
                        IceMachineRegister oldReg = vregOnPreg.get(mappedReg);
                        IceMachineRegister newReg = reg;
                        // 如果 目标物理寄存器上 当前有 虚拟寄存器映射，则先插入 STR
                        // 将 目标物理寄存器 上的 虚拟寄存器 写回对应 slot
                        if (vregOnPreg.containsKey(mappedReg)) {
                            IceStackSlot oldSlot = vregSlotMap.get(oldReg);

                            if (oldSlot == null) {
                                throw new IllegalArgumentException("Cannot find stack slot for register: " + oldReg + " or " + newReg);
                            }

                            // 插入存储指令，将 oldReg 的值存储到 oldSlot
                            IceMachineInstruction storeInst = new ARM64Instruction("STR {src}, {local:dst}",
                                    oldReg.createView(oldSlot.getType()), oldSlot);
                            storeInst.setParent(block);
                            block.add(pos, storeInst);
                            ++result;
                        }

                        // 如果 目标物理寄存器 上的 虚拟寄存器映射 不是当前的虚拟寄存器
                        // 则需要插入 LDR 指令
                        if (vregOnPreg.containsKey(mappedReg) && vregOnPreg.get(mappedReg).equals(newReg)) {
                            IceStackSlot slot = vregSlotMap.get(newReg);
                            if (slot == null) {
                                throw new IllegalArgumentException("Cannot find stack slot for register: " + newReg);
                            }

                            // 插入加载指令，将 slot 的值加载到 mappedReg
                            IceMachineInstruction loadInst = new ARM64Instruction("LDR {dst}, {local:src}",
                                    mappedReg.createView(rv.getType()), slot);
                            loadInst.setParent(block);
                            block.add(pos, loadInst);
                            ++result;

                            // 更新映射关系
                            vregOnPreg.put(mappedReg, newReg);
                        }
                        inst.replaceOperand(operand, mappedReg.createView(rv.getType()));
                    } else {
                        IceStackSlot slot = vregSlotMap.get(reg);
                        inst.replaceOperand(operand, slot);
                    }
                }
            }
        }

        return result;
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
                    if (operand instanceof IceStackSlot slot && slotVregMap.containsKey(slot)) {
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
