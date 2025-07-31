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
import top.voidc.misc.exceptions.MyTeammateGotIntoOUCException;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class LinearScanAllocator implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    private final LivenessAnalysis.LivenessResult livenessResult;
    private List<IceBlock> BBs;
    private Map<IceBlock, LivenessAnalysis.BlockLivenessData> functionLivenessData;
    private Map<IceMachineRegister, IceStackSlot> vregSlotMap;
    private Set<IceStackSlot> vregSlotSet;

    private List<LiveInterval> intervals;

    private List<LiveInterval> fixed; // 固定寄存器的区间
    private List<LiveInterval> unhandled; // 未处理的区间
    private List<LiveInterval> active; // 活跃的区间
    private List<LiveInterval> inactive; // 当前不占用寄存器但之后仍然活跃的区间

    private List<IceMachineRegister> freeRegisters; // 可用的物理寄存器

    private static class TempOperand extends IceValue {
        public IceStackSlot slot;
        public TempOperand(IceStackSlot slot, IceType type) {
            super();
            this.type = type;
            this.slot = slot;
        }
    }

    private static class LiveInterval {
        IceMachineRegister vreg, preg;
        int start, end;
        ArrayList<Integer> uses = new ArrayList<>(); // 记录使用位置

        public LiveInterval(IceMachineRegister vreg, int start, int end) {
            this.vreg = vreg;
            this.start = start;
            this.end = end;
            this.preg = null;
        }

        public int nextUseAfter(int pos) {
            for (int use : uses) {
                if (use > pos) {
                    return use;
                }
            }
            return Integer.MAX_VALUE; // 如果没有找到，返回一个很大的值
        }

        /**
         * 在指定位置分割当前区间
         *
         * @param pos 分割位置
         * @return 新的区间，如果无法分割则返回 null
         */
        public LiveInterval splitAt(int pos) {
            if (end - start == 1) {
                // 如果区间长度为1，不能再分割了
                return null;
            }

            if (pos <= start || pos > end) {
                throw new IllegalArgumentException("Cannot split interval at position: " + pos +
                        ", vreg: " + vreg + " preg: " + preg + " interval: [" + start + ", " + end + "]");
            }

            LiveInterval newInterval = new LiveInterval(vreg, pos, end);
            this.end = pos; // 更新当前区间的结束位置
            return newInterval; // 返回新的区间
        }

        public boolean isIntersecting(LiveInterval other) {
            return this.start < other.end && other.start < this.end;
        }

        public boolean isInvalid() {
            return start >= end || vreg == null;
        }
    }


    public LinearScanAllocator(LivenessAnalysis.LivenessResult livenessResult) {
        this.livenessResult = livenessResult;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        if (!(target instanceof ARM64Function mf)) {
            throw new IllegalArgumentException("LinearScanAllocator only supports ARM64Function.");
        }

        vregSlotMap = new HashMap<>();
        vregSlotSet = new HashSet<>();
        active = new ArrayList<>();

        functionLivenessData = livenessResult.getLivenessData(target);

        BBs = target.getBlocks();
        freeRegisters = getPhysicalRegisters(mf);

        unhandled = buildLiveIntervals(mf);
        unhandled.sort(Comparator.comparingInt(i -> i.start));
        intervals = new ArrayList<>(unhandled);

        Log.d("Initial live intervals: ");
        for (var interval : intervals) {
            Log.d("  " + interval.vreg + " [" + interval.start + ", " + interval.end + "]");
        }

        linearScan(mf);

        applyRegisterAllocation(mf);

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
                    if (operand instanceof IceMachineRegister.RegisterView rv && rv.getRegister().isVirtualize()) {
                        IceMachineRegister reg = rv.getRegister();
                        if (intervalMap.containsKey(reg)) {
                            // 如果已经存在这个寄存器的区间，更新结束位置
                            intervalMap.get(reg).end = Math.max(intervalMap.get(reg).end, currentIndex + 1);
                        } else {
                            // 如果不存在，创建新的区间
                            intervalMap.put(reg, new LiveInterval(reg, currentIndex, currentIndex + 1));
                        }
                        intervalMap.get(reg).uses.add(currentIndex);
                    }
                }

                IceMachineRegister.RegisterView rv = inst.getResultReg();
                if (rv != null && rv.getRegister().isVirtualize()) {
                    IceMachineRegister reg = rv.getRegister();
                    if (intervalMap.containsKey(reg)) {
                        // 如果已经存在这个寄存器的区间，更新结束位置
                        intervalMap.get(reg).end = Math.max(intervalMap.get(reg).end, currentIndex + 1);
                    } else {
                        // 如果不存在，创建新的区间
                        intervalMap.put(reg, new LiveInterval(reg, currentIndex, currentIndex + 1));
                    }
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

    private void linearScan(ARM64Function mf) {
        Log.d("Starting linear scan register allocation...");
        while (!unhandled.isEmpty()) {
            LiveInterval current = unhandled.removeFirst();
            Log.d("Processing interval: " + current.vreg + " [" + current.start + ", " + current.end + "]");
            expireOldIntervals(current);

            if (current.isInvalid()) {
                continue;
            }

            if (!freeRegisters.isEmpty()) {
                current.preg = freeRegisters.removeFirst();
                active.add(current);
            } else {
                spillAtInterval(current);
            }
        }

    }

    private List<IceMachineRegister> getPhysicalRegisters(ARM64Function mf) {
        ArrayList<IceMachineRegister> regPool = new ArrayList<>();

//        for (int i = 20; i <= 27; ++i) {
//            regPool.add(mf.getPhysicalRegister("x" + i));
//        }
//
//        for (int i = 9; i <= 15; ++i) {
//            regPool.add(mf.getPhysicalRegister("x" + i));
//        }

        regPool.add(mf.getPhysicalRegister("x21"));
        regPool.add(mf.getPhysicalRegister("x22"));
        regPool.add(mf.getPhysicalRegister("x23"));

        return regPool;
    }

    private void expireOldIntervals(LiveInterval current) {
        active.removeIf(LiveInterval::isInvalid);
        // 过期的区间是那些结束位置小于当前区间开始位置的区间
        active.removeIf(interval -> {
            if (interval.end > current.start) {
                return false; // 这个区间还活跃
            }
            if (interval.preg != null) {
                freeRegisters.addLast(interval.preg); // 将物理寄存器释放回寄存器池
            }
            return true; // 这个区间过期了
        });
    }

    private void processInactiveIntervals(LiveInterval current) {
        throw new MyTeammateGotIntoOUCException("TODO: May not be needed actually...");
    }

    private void spillAtInterval(LiveInterval current) {
        // 选择一个活跃区间进行溢出
        if (active.isEmpty()) {
            throw new IllegalStateException("No active intervals to spill.");
        }

        LiveInterval old = active.getLast();
        LiveInterval newInterval;
        if (old.nextUseAfter(current.start) > current.nextUseAfter(current.start)) {
            newInterval = old.splitAt(current.start + 1);

            if (newInterval == null) {
                // 若无法分割，则直接将当前区间添加到未处理列表
                newInterval = old;
            } else {
                // 分割出新区间，加入总列表
                intervals.add(newInterval);
            }

            unhandled.add(newInterval);

            unhandled.sort(Comparator.comparingInt(i -> i.start));
            active.sort(Comparator.comparingInt(i -> i.end));

            if (newInterval.preg != null) {
                freeRegisters.add(newInterval.preg);
                newInterval.preg = null;
            }
        } else {
            newInterval = current.splitAt(current.start + 1);
            if (newInterval == null) {
                // 如果无法分割，则直接 spill 当前区间
                Log.d("Spilling current interval: " + current.vreg + " [" + current.start + ", " + current.end + "]");
                // 在这里的实现就是什么都不做
            } else {
                // 分割出新区间，加入总列表
                intervals.add(newInterval);
                unhandled.add(current);
                unhandled.add(newInterval);
            }

            unhandled.sort(Comparator.comparingInt(i -> i.start));
        }
    }

    private void applyRegisterAllocation(ARM64Function mf) {
        Map<IceMachineRegister, IceMachineRegister> regMapping = new HashMap<>();
        for (var interval : intervals) {
            Log.d("LinearScanAllocator: Processing interval: " + interval.vreg + " [" + interval.start + ", " + interval.end + "], preg: " + interval.preg);
            if (interval.preg != null) {
                // 将虚拟寄存器映射到物理寄存器
                regMapping.put(interval.vreg, interval.preg);
            } else {
                if (vregSlotMap.containsKey(interval.vreg)) {
                    // 如果已经有映射，跳过
                    continue;
                }
                var slot = mf.allocateVariableStackSlot(interval.vreg.getType());
                vregSlotMap.putIfAbsent(interval.vreg, slot);
                vregSlotSet.add(slot);
            }
        }

        for (var block : BBs) {
            for (var iceInstruction : block) {
                if (!(iceInstruction instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function???");
                }

                replaceRegisters(inst, regMapping);
            }
        }

        insertSpillCode(mf);
    }

    /**
     * 替换指令中的虚拟寄存器为物理寄存器 / 栈槽
     *
     * @param inst 指令
     * @param regMapping 寄存器映射表，将虚拟寄存器映射到物理寄存器或栈槽
     */
    private void replaceRegisters(IceMachineInstruction inst, Map<IceMachineRegister, IceMachineRegister> regMapping) {
        for (int i = 0; i < inst.getOperands().size(); i++) {
            IceValue operand = inst.getOperand(i);
            if (operand instanceof IceMachineRegister.RegisterView rv) {
                IceMachineRegister reg = rv.getRegister();
                if (reg.isVirtualize()) {
                    // 如果是虚拟寄存器，替换为物理寄存器或栈槽
                    IceMachineRegister mappedReg = regMapping.getOrDefault(reg, null);
                    if (mappedReg != null) {
                        inst.replaceOperand(operand, mappedReg.createView(operand.getType()));
                    } else {
                        IceStackSlot slot = vregSlotMap.get(reg);
                        inst.replaceOperand(operand, new TempOperand(slot, operand.getType()));
                    }
                }
            }
        }

    }


    private void insertSpillCode(ARM64Function mf) {
        IceMachineRegister tempReg = mf.getPhysicalRegister("x19");
        IceStackSlot slotOnTempReg = null;

        for (var block : BBs) {
            for (int i = 0; i < block.size(); ++i) {
                if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                    throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function?????");
                }

                Map<IceValue, IceValue> replacements = new HashMap<>();

                for (IceValue operand : inst.getOperands()) {
                    if (operand instanceof TempOperand opr && vregSlotSet.contains(opr.slot)) {
                        IceStackSlot slot = opr.slot;
                        IceType type = opr.getType();
                        // 如果是栈槽，需要替换
                        if (slotOnTempReg != null) {
                            IceMachineInstruction storeInst = new ARM64Instruction("STR {src}, {local:dst}",
                                    tempReg.createView(slotOnTempReg.getType()), slotOnTempReg);
                            storeInst.setParent(block);
                            block.add(i, storeInst);
                            i++; // 插入后需要更新索引
                        }

                        var newOperand = tempReg.createView(type);
                        IceMachineInstruction loadInst = new ARM64Instruction("LDR {dst}, {local:src}",
                                newOperand, slot);
                        loadInst.setParent(block);
                        block.add(i, loadInst);
                        i++; // 插入后需要更新索引
                        replacements.put(operand, newOperand);
                        slotOnTempReg = slot;
                    }
                }

                for (var entry : replacements.entrySet()) {
                    inst.replaceOperand(entry.getKey(), entry.getValue());
                }

//                IceMachineRegister.RegisterView resultReg = inst.getResultReg();
//                if (resultReg != null && vregSlotMap.containsKey(resultReg.getRegister())) {
//                    IceMachineRegister reg = resultReg.getRegister();
//                    IceStackSlot slot = vregSlotMap.get(reg);
//
//                    // 创建存储指令
//                    IceMachineInstruction storeInst = new ARM64Instruction("STR {src}, {local:dst}",
//                            reg.createView(slot.getType()), slot);
//                    storeInst.setParent(block);
//                    block.add(i, storeInst);
//                    i++; // 插入后需要更新索引
//                }
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
