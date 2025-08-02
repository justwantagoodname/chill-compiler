package top.voidc.backend.regallocator;

import top.voidc.backend.LivenessAnalysis;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
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

    private static class RegisterPool {
        private final List<IceMachineRegister> pool;
        private final List<IceMachineRegister> allocated = new ArrayList<>();
        private final IceType poolType;

        public RegisterPool(List<IceMachineRegister> registers) {
            assert !registers.isEmpty() : "Register pool cannot be empty";
            this.pool = registers;
            this.poolType = registers.getFirst().getType();
        }

        public IceType getPoolType() {
            return poolType;
        }

        public Optional<IceMachineRegister> get() {
            if (pool.isEmpty()) {
                return Optional.empty();
            }
            var alloc = pool.removeFirst();
            allocated.add(alloc);
            return Optional.of(alloc);
        }

        public void release(IceMachineRegister register) {
            assert register.getType().equals(poolType);
            if (!allocated.remove(register)) {
                throw new IllegalStateException("Trying to release a register that was not allocated in this pool: " + register);
            }
            pool.add(register);
            pool.sort(Comparator.comparingInt(reg -> Integer.parseInt(reg.getName())));
        }

        public void releaseAll() {
            pool.addAll(allocated);
            allocated.clear();
            pool.sort(Comparator.comparingInt(reg -> Integer.parseInt(reg.getName())));
        }
    }

    private final LivenessAnalysis.LivenessResult livenessResult;
    private List<IceBlock> BBs;
    private Map<IceBlock, LivenessAnalysis.BlockLivenessData> functionLivenessData;

    /**
     * 仅构建某种类型寄存器的活跃区间并分配
     */
    private class TypedLinearScanAllocator {
        private final IceMachineFunction machineFunction;
        private final Map<IceMachineRegister, IceStackSlot> vregSlotMap = new HashMap<>();
        private final Set<IceStackSlot> vregSlotSet = new HashSet<>();

        private final List<LiveInterval> intervals;

        private List<LiveInterval> fixed; // 固定寄存器的区间
        private final List<LiveInterval> unhandled; // 未处理的区间
        private final List<LiveInterval> active = new ArrayList<>(); // 活跃的区间
        private List<LiveInterval> inactive; // 当前不占用寄存器但之后仍然活跃的区间

        private final RegisterPool registerPool;

        public TypedLinearScanAllocator(IceMachineFunction machineFunction, RegisterPool registerPool) {
            this.registerPool = registerPool;
            this.machineFunction = machineFunction;

            unhandled = buildLiveIntervals();
            unhandled.sort(Comparator.comparingInt(i -> i.start));
            intervals = new ArrayList<>(unhandled);

            Log.d("Initial live intervals: ");
            for (var interval : intervals) {
                Log.d("  " + interval.vreg + " [" + interval.start + ", " + interval.end + "]");
            }


            linearScan(); // 执行线性扫描寄存器分配
        }

        private void linearScan() {
            while (!unhandled.isEmpty()) {
                LiveInterval current = unhandled.removeFirst();
                expireOldIntervals(current);

                var newPhyReg = registerPool.get();
                if (newPhyReg.isPresent()) {
                    current.preg = newPhyReg.get();
                    active.add(current);
                } else {
                    spillAtInterval(current);
                }
            }

        }

        private List<LiveInterval> buildLiveIntervals() {
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
                        if (operand instanceof IceMachineRegister.RegisterView rv && rv.getRegister().isVirtualize()
                                && rv.getRegister().getType().equals(registerPool.getPoolType())) { // 仅处理指定类型的寄存器
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
                    if (rv != null && rv.getRegister().isVirtualize() && rv.getRegister().getType().equals(registerPool.getPoolType())) { // 仅处理指定类型的寄存器
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

        private void expireOldIntervals(LiveInterval current) {
            // 过期的区间是那些结束位置小于当前区间开始位置的区间
            active.removeIf(interval -> {
                if (interval.end >= current.start) {
                    return false; // 这个区间还活跃
                }
                registerPool.release(interval.preg); // 将物理寄存器释放回寄存器池
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
                newInterval = old.splitAt(current.start);
                unhandled.add(newInterval);

                unhandled.sort(Comparator.comparingInt(i -> i.start));
                active.sort(Comparator.comparingInt(i -> i.end));

                registerPool.release(newInterval.preg);
                newInterval.preg = null;

            } else {
                newInterval = current.splitAt(current.nextUseAfter(current.start));
                unhandled.add(current);
                unhandled.add(newInterval);

                unhandled.sort(Comparator.comparingInt(i -> i.start));
            }
            intervals.add(newInterval);
        }

        private void applyRegisterAllocation() {
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
                    var slot = machineFunction.allocateVariableStackSlot(interval.vreg.getType());
                    vregSlotMap.putIfAbsent(interval.vreg, slot);
                    vregSlotSet.add(slot);
                }
            }

            for (var block : BBs) {
                for (int i = 0; i < block.size(); ++i) {
                    if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                        throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function???");
                    }

                    replaceRegisters(inst, regMapping);
                }
            }

            insertSpillCode();
        }

        /**
         * 替换指令中的虚拟寄存器为物理寄存器 / 栈槽
         *
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
                            inst.replaceOperand(operand, slot);
                        }
                    }
                }
            }

        }


        private void insertSpillCode() {
            IceMachineRegister tempReg = machineFunction.getPhysicalRegister("x19");
            IceStackSlot slotOnTempReg = null;

            for (var block : BBs) {
                for (int i = 0; i < block.size(); ++i) {
                    if (!(block.get(i) instanceof IceMachineInstruction inst)) {
                        throw new IllegalArgumentException("Why there is a non-machine instruction in a machine function?????");
                    }

                    Map<IceValue, IceValue> replacements = new HashMap<>();

                    for (IceValue operand : inst.getSourceOperands()) {
                        if (operand instanceof IceStackSlot slot && vregSlotSet.contains(slot)) {
                            // 如果是栈槽，需要替换
                            if (slotOnTempReg != null) {
                                IceMachineInstruction storeInst = new ARM64Instruction("STR {src}, {local:dst}",
                                        tempReg.createView(slotOnTempReg.getType()), slotOnTempReg);
                                storeInst.setParent(block);
                                block.add(i, storeInst);
                                i++; // 插入后需要更新索引
                            }

                            var newOperand = tempReg.createView(slot.getType());
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

                    IceMachineRegister.RegisterView resultReg = inst.getResultReg();
                    if (resultReg != null && vregSlotMap.containsKey(resultReg.getRegister())) {
                        IceMachineRegister reg = resultReg.getRegister();
                        IceStackSlot slot = vregSlotMap.get(reg);

                        // 创建存储指令
                        IceMachineInstruction storeInst = new ARM64Instruction("STR {src}, {local:dst}",
                                reg.createView(slot.getType()), slot);
                        storeInst.setParent(block);
                        block.add(i, storeInst);
                        i++; // 插入后需要更新索引
                    }
                }


            }
        }

    }

    private RegisterPool xRegPool; // 整数寄存器池
    private RegisterPool vRegPool; // 浮点寄存器池

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

        public LiveInterval splitAt(int pos) {
            if (pos <= start || pos >= end) {
                throw new IllegalArgumentException("Cannot split interval at position: " + pos);
            }
            LiveInterval newInterval = new LiveInterval(vreg, pos, end);
            this.end = pos; // 更新当前区间的结束位置
            return newInterval; // 返回新的区间
        }

        public boolean isIntersecting(LiveInterval other) {
            return this.start < other.end && other.start < this.end;
        }
    }

    public LinearScanAllocator(LivenessAnalysis.LivenessResult livenessResult) {
        this.livenessResult = livenessResult;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        functionLivenessData = livenessResult.getLivenessData(target);

        BBs = target.getBlocks();
        initPhysicalRegisterPool(target);

        Log.d("开始分配整数寄存器");
        var integerAllocator = new TypedLinearScanAllocator(target, xRegPool);
        integerAllocator.applyRegisterAllocation(); // 应用寄存器分配结果

        Log.d("开始分配浮点寄存器");
        var floatAllocator = new TypedLinearScanAllocator(target, vRegPool);

         floatAllocator.applyRegisterAllocation(); // 应用寄存器分配结果



        return true;
    }

    /**
     * 设置寄存器池
     * TODO 完成 Caller Save 使用后给寄存器池添加Caller Save寄存器
     */
    private void initPhysicalRegisterPool(IceMachineFunction mf) {
        var xRegs = new ArrayList<IceMachineRegister>();
        for (var i = 19; i <= 27; ++i) {
            xRegs.add(mf.getPhysicalRegister("x" + i));
        }

        xRegPool = new RegisterPool(xRegs);

        var vRegs = new ArrayList<IceMachineRegister>();

        for (var i = 8; i <= 15; ++i) {
            vRegs.add(mf.getPhysicalRegister("v" + i));
        }

        vRegPool = new RegisterPool(vRegs);
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
    public int getArchitectureBitSize() {
        return 64;
    }
}
