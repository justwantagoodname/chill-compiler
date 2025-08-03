package top.voidc.backend.regallocator;

import top.voidc.backend.LivenessAnalysis;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.*;

import top.voidc.misc.Flag;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.ds.BiMap;
import top.voidc.misc.exceptions.MyTeammateGotIntoOUCException;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"})
public class LinearScanAllocator implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    private static class RegisterPool {
        private final PriorityQueue<IceMachineRegister> pool;
        private final Set<IceMachineRegister> allocated = new HashSet<>();
        private final IceType poolType;

        public RegisterPool(List<IceMachineRegister> registers) {
            assert !registers.isEmpty() : "Register pool cannot be empty";
            this.poolType = registers.getFirst().getType();
            // 使用优先队列（小顶堆）按寄存器名字中的数字排序
            this.pool = new PriorityQueue<>(Comparator.comparingInt(reg -> Integer.parseInt(reg.getName())));
            this.pool.addAll(registers);
        }

        public IceType getPoolType() {
            return poolType;
        }

        public Optional<IceMachineRegister> get() {
            IceMachineRegister alloc = pool.poll();
            if (alloc == null) {
                return Optional.empty();
            }
            allocated.add(alloc);
            return Optional.of(alloc);
        }

        public void release(IceMachineRegister register) {
            assert register.getType().equals(poolType);
            if (!allocated.remove(register)) {
                throw new IllegalStateException("Trying to release a register that was not allocated in this pool: " + register);
            }
            pool.offer(register);
        }

        public void releaseAll() {
            pool.addAll(allocated);
            allocated.clear();
        }
    }

    private final LivenessAnalysis.LivenessResult livenessResult;

    /**
     * 仅构建某种类型寄存器的活跃区间并分配
     */
    private class TypedLinearScanAllocator {
        private final IceMachineFunction machineFunction;
        private final Map<IceBlock, LivenessAnalysis.BlockLivenessData> functionLivenessData;
        private final List<IceBlock> BBs;
        private final BiMap<IceMachineInstruction, Integer> instructionIds = new BiMap<>();
        private final Map<IceMachineRegister, IceStackSlot> vregSlotMap = new HashMap<>();
        private final Map<IceMachineRegister, IceMachineRegister> regMapping = new HashMap<>();

        private final List<LiveInterval> intervals = new ArrayList<>();

        private List<LiveInterval> fixed; // 固定寄存器的区间
        private final List<LiveInterval> unhandled = new ArrayList<>(); // 未处理的区间
        private final List<LiveInterval> active = new ArrayList<>(); // 活跃的区间
        private List<LiveInterval> inactive; // 当前不占用寄存器但之后仍然活跃的区间

        private final RegisterPool registerPool;
        private final RegisterPool scratchRegisterPool;

        private static final int INSTRUCTION_ID_STEP = 2; // 每条指令的ID步长

        public TypedLinearScanAllocator(IceMachineFunction machineFunction, RegisterPool registerPool, RegisterPool scratchRegisterPool) {
            this.BBs = machineFunction.getBlocks();
            this.functionLivenessData = livenessResult.getLivenessData(machineFunction);
            this.registerPool = registerPool;
            this.machineFunction = machineFunction;
            this.scratchRegisterPool = scratchRegisterPool;
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

        public void buildLiveIntervals() {
            Map<IceMachineRegister, LiveInterval> intervalMap = new HashMap<>();

            int currentIndex = 0;
            for (var block : BBs) {
                for (var instruction : block) {
                    if (instruction instanceof IceMachineInstructionComment) continue;
                    instructionIds.put((IceMachineInstruction) instruction, currentIndex);
                    currentIndex += INSTRUCTION_ID_STEP;

                    for (var operand : instruction.getOperands()) {
                        // TODO: 想想物理寄存器怎么处理
                        if (operand instanceof IceMachineRegister.RegisterView registerView
                                && registerView.getRegister().isVirtualize()
                                && registerView.getRegister().getType().equals(registerPool.getPoolType())) {
                            intervalMap.computeIfAbsent(registerView.getRegister(), LiveInterval::new);
                        }
                    }
                }
            }

            for (var block : BBs) {
                var liveOut = functionLivenessData.get(block).liveOut();

                var blockStartId = instructionIds.getValue((IceMachineInstruction) block.getFirst());
                var blockEndId = instructionIds.getValue((IceMachineInstruction) block.getLast());

                for (var value : liveOut) {
                    if (value instanceof IceMachineRegister register && intervalMap.containsKey(register)) {
                        var interval = intervalMap.get(register);
                        interval.addRange(blockStartId, blockEndId);
                    }
                }

                for (var i = block.size() - 1; i >= 0; --i) {
                    var instruction = (IceMachineInstruction) block.get(i);
                    if (instruction instanceof IceMachineInstructionComment) continue;

                    var instructionId = instructionIds.getValue(instruction);

                    // 先处理Def
                    if (instruction.getResultReg() != null && intervalMap.containsKey(instruction.getResultReg().getRegister())) {
                        var interval = intervalMap.get(instruction.getResultReg().getRegister());
                        interval.addUse(instructionId); // 记录使用位置
                        interval.removeRange(blockStartId, instructionId); // 此指令重新定义了这个寄存器 需要删除从块开始到现在的定义
                    }

                    // 后处理 Use 如果一个一条指令同时使用和定义 按此顺序就能正确处理
                    for (var operand : instruction.getSourceOperands()) {
                        if (operand instanceof IceMachineRegister.RegisterView registerView
                            && intervalMap.containsKey(registerView.getRegister())) {
                            var interval = intervalMap.get(registerView.getRegister());
                            interval.addUse(instructionId);
                            interval.addRange(blockStartId, instructionId);
                        }
                    }
                }
            }

            unhandled.addAll(intervalMap.values());
            unhandled.forEach(LiveInterval::updateStartEnd);
            unhandled.sort(Comparator.comparingInt(i -> ((LiveInterval) i).start)
                    .thenComparing(i -> ((LiveInterval) i).end));
            intervals.addAll(unhandled);

            Log.d("Initial live intervals: ");
            for (var interval : intervals) {
                Log.d("  " + interval.vreg + " [" + interval.start + ", " + interval.end + "]");
            }
        }

        private void expireOldIntervals(LiveInterval current) {
            // 过期的区间是那些结束位置小于当前区间开始位置的区间
            active.removeIf(interval -> {
                if (interval.end > current.start) {
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

            LiveInterval spillCandidate = active.stream().max(Comparator.comparingInt(interval -> interval.end)).orElseThrow();

            // 选择结束位置更大的区间进行溢出 相等时优先溢出 current
            if (spillCandidate.end > current.end) {
                // 溢出当前在active中的区间 spillCandidate
                current.preg = spillCandidate.preg;
                spillCandidate.preg = null; // 清除物理寄存器映射 标志溢出

                active.remove(spillCandidate); // 从活跃区间中移除
                active.add(current);
            } else {
                // 溢出当前区间
                current.preg = null;
            }
        }

        /**
         * 利用活跃区间生成寄存器分配结果
         */
        private void createAllocationResult() {
            for (var interval : intervals) {
                Log.d("LinearScanAllocator: Processing interval: " + interval.vreg + " [" + interval.start + ", " + interval.end + "], preg: " + interval.preg);
                if (false) { // FIXME: 先让全部变量溢出
                    // 将虚拟寄存器映射到物理寄存器
                    regMapping.put(interval.vreg, interval.preg);
                } else {
                    // 溢出变量
                    var slot = vregSlotMap.computeIfAbsent(interval.vreg, register ->
                            machineFunction.allocateVariableStackSlot(register.getType()));
                    var alignment = switch (interval.vreg.getType().getTypeEnum()) {
                        case I32, F32 -> 4;
                        case I64, F64, PTR -> 8;
                        case VEC -> 16; // VEC128 对齐到 16 字节
                        default -> throw new IllegalArgumentException("Unsupported type: " + interval.vreg.getType());
                    };
                    slot.setAlignment(alignment);
                }
            }
        }
        /**
         * 应用寄存器分配结果
         */
        public void applyRegisterAllocation() {
            var showDebug = Boolean.TRUE.equals(Flag.get("-fshow-trace-info"));

            createAllocationResult();

            for (var block : BBs) {
                for (int i = 0; i < block.size(); ++i) {
                    var instruction = (IceMachineInstruction)  block.get(i);
                    if (instruction instanceof IceMachineInstructionComment) continue;
                    var loadSourceOperandsInstructions = new ArrayList<IceMachineInstruction>(); // 用于存储加载源操作数的指令

                    var dstReg = instruction.getResultReg(); // 一定要在插入加载指令前获取目标寄存器因为有可能在和源操作数重合的情况下被覆盖
                    IceMachineRegister dstReplacedReg = null; // 记录目标寄存器是否被替换过如果被替换过，如果替换过那么在替换源操作数时也要替换成同一个物理寄存器

                    for (var operand : List.copyOf(instruction.getSourceOperands())) {
                        if (operand instanceof IceMachineRegister.RegisterView registerView) {
                            if (regMapping.containsKey(registerView.getRegister())) {
                                // 物理寄存器
                                var preg = regMapping.get(registerView.getRegister());
                                instruction.replaceOperand(operand, preg.createView(registerView.getType()));

                                if (dstReg != null && dstReg.getRegister().equals(registerView.getRegister())) {
                                    // 如果目标寄存器也是这个虚拟寄存器，记录下来 以便分配同一个物理寄存器
                                    dstReplacedReg = preg; // 记录被替换的寄存器
                                }
                            } else if (vregSlotMap.containsKey(registerView.getRegister())) {
                                // 溢出变量
                                var slot = vregSlotMap.get(registerView.getRegister()); // 获取对应的栈槽
                                Optional<IceMachineRegister> scratchRegister;
                                // 先申请一个scratchRegister
                                if (dstReg == null || !dstReg.getRegister().equals(registerView.getRegister()) // 没有目标操作数或者和目标操作数不是一个
                                    || (dstReg.getRegister().equals(registerView.getRegister()) && dstReplacedReg == null)) { // 或者是目标操作数但是还没有分配过
                                    scratchRegister = scratchRegisterPool.get();
                                    // 如果没有可用的scratchRegister，抛出异常
                                    if (scratchRegister.isEmpty()) {
                                        throw new IllegalStateException("No available scratch register, Try to increase");
                                    }
                                } else {
                                    // 如果目标寄存器和源操作数重合且已经被替换过，那么使用之前的scratchRegister
                                    assert dstReplacedReg != null;
                                    scratchRegister = Optional.of(dstReplacedReg);
                                }

                                var scratchRegisterView = scratchRegister.get().createView(registerView.getType());

                                // 生成ldr指令
                                var load = new ARM64Instruction("LDR {dst}, {local:src}" + (showDebug ? " //" + registerView.getRegister().getReferenceName() : ""),
                                        scratchRegisterView, slot);
                                loadSourceOperandsInstructions.add(load);

                                instruction.replaceOperand(operand, scratchRegisterView); // 替换原指令的虚拟寄存器操作数为溢出专用寄存器

                                // 如果目标寄存器也是这个虚拟寄存器，记录下来 以便分配同一个物理寄存器
                                if (dstReg != null && dstReg.getRegister().equals(registerView.getRegister())) {
                                    dstReplacedReg = scratchRegister.get(); // 记录被替换的寄存器
                                }
                            }
                        }
                    }

                    if (!loadSourceOperandsInstructions.isEmpty()) {
                        loadSourceOperandsInstructions.forEach(instr -> instr.setParent(block));
                        block.addAll(i, loadSourceOperandsInstructions); // 在原指令前插入加载指令
                        i += loadSourceOperandsInstructions.size(); // 更新索引，跳过插入的加载指令
                    }

                    if (dstReg != null) {
                        if (regMapping.containsKey(dstReg.getRegister())) {
                            // 物理寄存器
                            if (dstReplacedReg == null || instruction.getResultReg().getRegister().isVirtualize()) {
                                // 目标寄存器没有被替换过 或者 如果存在已经替换的物理寄存器但是目标寄存器还没有被替换，这发生在目标寄存器和源操作数重合但视图不一致的情况下
                                var preg = regMapping.get(dstReg.getRegister());
                                instruction.replaceOperand(dstReg, preg.createView(dstReg.getType()));
                            }
                        } else if (vregSlotMap.containsKey(dstReg.getRegister())) {
                            // 溢出变量
                            var slot = vregSlotMap.get(dstReg.getRegister()); // 获取对应的栈槽
                            if (dstReplacedReg == null || instruction.getResultReg().getRegister().isVirtualize()) { // 如果目标寄存器没有被替换过

                                if (dstReplacedReg == null) {
                                    // 先申请一个scratchRegister
                                    var scratchRegister = scratchRegisterPool.get();
                                    // 如果没有可用的scratchRegister，抛出异常
                                    if (scratchRegister.isEmpty()) {
                                        throw new IllegalStateException("No available scratch register, Try to increase");
                                    }
                                    dstReplacedReg = scratchRegister.get();
                                }

                                instruction.replaceOperand(dstReg, dstReplacedReg.createView(dstReg.getType())); // 替换原指令的虚拟寄存器操作数为溢出专用寄存器
                            }

                            // 生成str指令
                            // 确保使用相同的物理寄存器存储溢出的目标虚拟寄存器
                            var store = new ARM64Instruction("STR {src}, {local:target}" + (showDebug ? " //" + dstReg.getRegister().getReferenceName() : ""),
                                    dstReplacedReg.createView(dstReg.getType()), slot);
                            store.setParent(block);
                            block.add(i + 1, store); // 在原指令后插入存储指令
                            i++; // 跳过存储指令
                        }
                    }
                    scratchRegisterPool.releaseAll(); // 释放所有的scratch寄存器
                }
            }
        }
    }

    private record AllRegisterPools(
            RegisterPool xRegPool,
            RegisterPool xScratchPool,
            RegisterPool vRegPool,
            RegisterPool vScratchPool
    ) {}

    private static class LiveInterval {
        public record Range(int start, int end) { // 范围是闭区间 [start, end]

            // 区间可能为一个点
            public Range {
                assert start <= end : "Range start must be less than or equal to end";
            }

            public boolean contains(int pos) {
                return pos >= start && pos <= end;
            }

            /**
             * 判断此区间是否与另一个区间相交，即使端点相交也算相交
             * @param other 另一个区间
             * @return 如果此区间与另一个区间相交则返回true，否则返回false
             */
            public boolean isIntersecting(Range other) {
                return this.start <= other.end && other.start <= this.end;
            }

            /**
             * 从此区间中移除与另一个区间的交集部分
             * 如果没有交集则返回原区间
             * @param other 要移除的部分
             * @return 一个区间列表，表示从此区间中移除与另一个区间的交集部分后剩余的区间
             */
            public List<Range> removeIntersection(Range other) {
                if (!this.isIntersecting(other)) {
                    return List.of(this); // 没有交集，返回原区间
                }
                
                // 如果other完全包含this，返回空列表表示区间被完全移除
                if (other.start <= this.start && other.end >= this.end) {
                    return List.of();
                }
                
                // 如果this的开始部分与other相交，保留后面的部分
                if (other.start <= this.start && other.end < this.end) {
                    return List.of(new Range(other.end + 1, this.end));
                }
                
                // 如果this的结束部分与other相交，保留前面的部分
                if (other.start > this.start && other.end >= this.end) {
                    return List.of(new Range(this.start, other.start - 1));
                }
                
                // 如果other在this中间，区间被分裂成两部分
                if (other.start > this.start && other.end < this.end) {
                    return List.of(
                        new Range(this.start, other.start - 1),
                        new Range(other.end + 1, this.end)
                    );
                }
                
                // 其他情况，返回原区间（理论上不应该到达这里）
                return List.of(this);
            }

            /**
             * 合并两个区间
             * @param other 另一个区间
             * @return 一个新的区间，表示合并后的区间
             * @throws AssertionError 如果两个区间不相交
             */
            public Range merge(Range other) {
                assert this.isIntersecting(other) : "Cannot merge non-intersecting ranges";
                return new Range(Math.min(this.start, other.start), Math.max(this.end, other.end));
            }
        }

        private final List<Range> livedRanges = new ArrayList<>();

        IceMachineRegister vreg, preg;
        int start, end;
        private Set<Integer> uses = new HashSet<>(); // 记录使用位置

        public LiveInterval(IceMachineRegister vreg) {
            this(vreg, -1, -1);
        }

        private LiveInterval(IceMachineRegister vreg, int start, int end) {
            this.vreg = vreg;
            this.start = start;
            this.end = end;
            this.preg = null;
        }

        public void addRange(int start, int end) {
            Range newRange = new Range(start, end);
            
            // 使用迭代器遍历并合并相交的区间
            Iterator<Range> iterator = livedRanges.iterator();
            while (iterator.hasNext()) {
                Range existingRange = iterator.next();
                if (newRange.isIntersecting(existingRange)) {
                    // 合并相交的区间
                    newRange = newRange.merge(existingRange);
                    iterator.remove(); // 移除已合并的区间
                }
            }

            // 添加合并后的新区间
            livedRanges.add(newRange);
            
            // 更新整体的开始和结束位置
            updateStartEnd();
        }

        public void removeRange(int start, int end) {
            Range rangeToRemove = new Range(start, end);
            List<Range> updatedRanges = new ArrayList<>();

            for (Range existingRange : livedRanges) {
                // 从每个现有区间中移除指定区间
                List<Range> remainingRanges = existingRange.removeIntersection(rangeToRemove);
                updatedRanges.addAll(remainingRanges);
            }

            // 更新区间列表
            livedRanges.clear();
            livedRanges.addAll(updatedRanges);
            
            // 更新整体的开始和结束位置
            updateStartEnd();
        }

        public void updateStartEnd() {
            if (livedRanges.isEmpty()) {
                start = -1;
                end = -1;
            } else {
                start = livedRanges.stream().mapToInt(range -> range.start).min().orElse(-1);
                end = livedRanges.stream().mapToInt(range -> range.end).max().orElse(-1);
            }
        }

        public void addUse(int position) {
            uses.add(position);
        }
    }

    public LinearScanAllocator(LivenessAnalysis.LivenessResult livenessResult) {
        this.livenessResult = livenessResult;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        var registerPools = initPhysicalRegisterPool(target);

        Log.d("开始分配整数寄存器");
        var integerAllocator = new TypedLinearScanAllocator(target, registerPools.xRegPool(), registerPools.xScratchPool());
        integerAllocator.buildLiveIntervals();
        integerAllocator.linearScan();
        integerAllocator.applyRegisterAllocation(); // 应用寄存器分配结果

        Log.d("开始分配浮点寄存器");
        var floatAllocator = new TypedLinearScanAllocator(target, registerPools.vRegPool(), registerPools.vScratchPool());
        floatAllocator.buildLiveIntervals();
        floatAllocator.linearScan();
        floatAllocator.applyRegisterAllocation(); // 应用寄存器分配结果



        return true;
    }

    /**
     * 设置寄存器池
     * TODO 完成 Caller Save 使用后给寄存器池添加Caller Save寄存器
     */
    private AllRegisterPools initPhysicalRegisterPool(IceMachineFunction mf) {
        
        var xRegs = new ArrayList<IceMachineRegister>();
        for (var i = 19; i <= 27; ++i) {
            xRegs.add(mf.getPhysicalRegister("x" + i));
        }

        var xRegPool = new RegisterPool(xRegs.subList(0, 5)); // x19 - x23
        var xScratchPool = new RegisterPool(xRegs.subList(5, 9)); // x24 - x27

        var vRegs = new ArrayList<IceMachineRegister>();

        for (var i = 8; i <= 15; ++i) {
            vRegs.add(mf.getPhysicalRegister("v" + i));
        }

        var vRegPool = new RegisterPool(vRegs.subList(0, 4)); // v8 - v11
        var vScratchPool = new RegisterPool(vRegs.subList(4, 8)); // v12 - v15

        return new AllRegisterPools(xRegPool, xScratchPool, vRegPool, vScratchPool);
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
