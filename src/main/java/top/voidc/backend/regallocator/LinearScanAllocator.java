package top.voidc.backend.regallocator;

import top.voidc.backend.LivenessAnalysis;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.*;

import top.voidc.misc.Flag;
import top.voidc.misc.Log;
import top.voidc.misc.Tool;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.ds.BiMap;
import top.voidc.misc.exceptions.MyTeammateGotIntoOUCException;
import top.voidc.optimizer.pass.CompilePass;

import java.util.*;

@Pass(group = {"O0", "backend"}, parallel = true)
public class LinearScanAllocator implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    private static final int INSTRUCTION_ID_STEP = 2; // 每条指令的ID步长

    private static class RegisterPool {
        private final PriorityQueue<IceMachineRegister> pool;
        private final BiMap<IceMachineRegister, LiveInterval> allocated = new BiMap<>();
        private final IceType poolType;
        private final List<LiveInterval> fixed = new ArrayList<>(); // 固定寄存器的区间

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

        public void forceUseRegister(LiveInterval user, IceMachineRegister register) {
            assert register.getType().equals(poolType) : "Register type mismatch: " + register.getType() + " vs " + poolType;
            if (!pool.contains(register) && !allocated.containsKey(register)) {
                return;
            }

            if (!pool.remove(register)) {
                if (getUser(register).equals(user)) return; // 如果这个寄存器已经被分配给了这个用户就不需要从池中移除
                throw new IllegalStateException("Trying to force use a register that is not in the pool: " + register);
            }
            allocated.put(register, user);
        }

        public Optional<IceMachineRegister> get(LiveInterval user) {
            if (user == null) { // 传入 null 值就任意分配寄存器
                IceMachineRegister alloc = pool.poll();
                if (alloc == null) {
                    return Optional.empty();
                }
                allocated.put(alloc, new LiveInterval(null));
                return Optional.of(alloc);
            }
            // 启发式算法如果该用户和未来预着色区间有重合那就不分配对应的预着色寄存器
            assert !user.isPrecolored() : "Cannot allocate precolored register: " + user.vreg;

            var userRange = new LiveInterval.Range(user.start, user.end);
            var dangerRegisters = fixed.stream()
                    .filter(interval -> interval.livedRanges.stream().anyMatch(range -> range.isIntersecting(userRange)))
                    .map(interval -> interval.preg)
                    .toList();

            // 从池中获取一个寄存器同时避开预着色寄存器
            IceMachineRegister alloc;
            var candidates = pool.stream()
                    .filter(reg -> !dangerRegisters.contains(reg) && !allocated.containsKey(reg));

            // 如果跨越了调用的区间则尽量分配 callee-save 寄存器

            if (user.isCrossCall()) {
                // 先尝试分配 callee-save 寄存器没有再尝试分配 caller-save 寄存器
                var candidatesList = candidates.toList();
                alloc = candidatesList.stream()
                        .filter(reg -> Tool.getArm64RegisterType(reg) == Tool.RegisterType.CALLEE_SAVED)
                        .findFirst().orElseGet(
                                () -> candidatesList.stream()
                                        .filter(reg -> Tool.getArm64RegisterType(reg) == Tool.RegisterType.CALLER_SAVED)
                                        .findFirst().orElse(null)
                        );
            } else {
                alloc = candidates.findFirst().orElse(null);
            }

            if (alloc == null) {
                return Optional.empty(); // 没有可用的寄存器
            }
            allocated.put(alloc, user);
            pool.remove(alloc); // 从池中移除已分配的寄存器
            return Optional.of(alloc);
        }

        public void release(IceMachineRegister register) {
            assert register.getType().equals(poolType);
            if (allocated.removeByKey(register) == null) {
                throw new IllegalStateException("Trying to release a register that was not allocated in this pool: " + register);
            }
            pool.offer(register);
        }

        public void releaseAll() {
            pool.addAll(allocated.keySet());
            allocated.clear();
        }

        public LiveInterval getUser(IceMachineRegister register) {
            assert register.getType().equals(poolType) && allocated.containsKey(register);
            return allocated.getValue(register);
        }

        public void addFixedIntervals(Collection<LiveInterval> intervals) {
            fixed.addAll(intervals);
            fixed.sort(Comparator.comparingInt(i -> i.start));
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

        private final List<LiveInterval> fixed = new ArrayList<>(); // 预着色寄存器的区间
        private final List<LiveInterval> unhandled = new ArrayList<>(); // 未处理的区间
        private final List<LiveInterval> active = new ArrayList<>(); // 活跃的区间

        private final List<Integer> callPositions = new ArrayList<>(); // 记录调用指令的位置
        private final Map<Integer, List<LiveInterval>> callIntervals = new HashMap<>(); // 记录调用指令对应的活跃区间

        private final Map<IceMachineRegister, IceStackSlot.VariableStackSlot> callerSavedSlots = new HashMap<>(); // 用于保存 caller-save 寄存器的栈槽

        private final RegisterPool registerPool;
        private final RegisterPool scratchRegisterPool;

        public TypedLinearScanAllocator(IceMachineFunction machineFunction, RegisterPool registerPool, RegisterPool scratchRegisterPool) {
            this.BBs = machineFunction.getBlocks();
            this.functionLivenessData = livenessResult.getLivenessData(machineFunction);
            this.registerPool = registerPool;
            this.machineFunction = machineFunction;
            this.scratchRegisterPool = scratchRegisterPool;
        }

        private void linearScan() {
            while (!unhandled.isEmpty()) {
                // TODO: 添加寄存器合并逻辑
                LiveInterval current = unhandled.removeFirst();
                expireOldIntervals(current);

                if (current.isPrecolored()) {
                    active.add(current);
                    try {
                        registerPool.forceUseRegister(current, current.preg); // 强制使用预着色寄存器
                    } catch (IllegalStateException e) {
                        // 如果被使用了就需要强制溢出使用了的区间
                        var victim = registerPool.getUser(current.preg);
                        if (victim != null) {
                            Log.d("因为预着色强制溢出 " + current.preg + " used by " + victim.vreg);
                            registerPool.release(victim.preg);
                            victim.preg = null;
                            registerPool.forceUseRegister(current, current.preg); // 重新强制使用预着色寄存器
                        } else {
                            throw new IllegalStateException("Precolored register " + current.preg + " is not allocated to any interval.");
                        }
                    }
                } else {
                    var newPhyReg = registerPool.get(current);
                    if (newPhyReg.isPresent()) {
                        current.preg = newPhyReg.get();
                        active.add(current);
                    } else {
                        spillAtInterval(current);
                    }
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

                    if (((IceMachineInstruction) instruction).getOpcode().equals("BL")) {
                        // 记录调用指令的位置
                        callPositions.add(currentIndex);
                    }

                    currentIndex += INSTRUCTION_ID_STEP;

                    for (var operand : instruction.getOperands()) {
                        if (operand instanceof IceMachineRegister.RegisterView registerView
                                && registerView.getRegister().getType().equals(registerPool.getPoolType())) {
                            if (registerView.getRegister().isVirtualize()) {
                                // 虚拟寄存器
                                intervalMap.computeIfAbsent(registerView.getRegister(), LiveInterval::new);
                            } else {
                                // 物理寄存器
                                if (!registerView.getRegister().getName().equals("zr")) { // 零寄存器不需要处理
                                    intervalMap.computeIfAbsent(registerView.getRegister(), reg -> new LiveInterval(reg, true));
                                }
                            }
                        }
                    }
                }
            }

            for (var block : BBs) {
                var liveOut = functionLivenessData.get(block).liveOut();

                var validInstructions = new ArrayList<>(
                        block.stream().filter(inst -> !(inst instanceof IceMachineInstructionComment)).map(inst -> ((IceMachineInstruction) inst)).toList());


                var blockStartId = instructionIds.getValue(validInstructions.getFirst());
                var blockEndId = instructionIds.getValue(validInstructions.getLast());

                Collections.reverse(validInstructions); // 倒序遍历指令

                for (var value : liveOut) {
                    if (value instanceof IceMachineRegister register && intervalMap.containsKey(register)) {
                        var interval = intervalMap.get(register);
                        interval.addRange(blockStartId, blockEndId);
                    }
                }

                for (var instruction : validInstructions) {
                    assert !(instruction instanceof IceMachineInstructionComment);

                    var instructionId = instructionIds.getValue(instruction);

                    // 先处理Def
                    if (instruction.getResultReg(true) != null && intervalMap.containsKey(instruction.getResultReg(true).getRegister())) {
                        var interval = intervalMap.get(instruction.getResultReg(true).getRegister());
                        interval.addUse(instructionId); // 记录使用位置
                        if (!instructionId.equals(blockStartId)) // 此指令重新定义了这个寄存器 需要删除从块开始到前一条指令的活跃区间，如果此指令同时使用了由 def 保证
                            interval.removeRange(blockStartId, instructionId - INSTRUCTION_ID_STEP);
                    }

                    // 后处理 Use 如果一个一条指令同时使用和定义 按此顺序就能正确处理
                    for (var operand : instruction.getSourceOperands(true)) {
                        if (operand instanceof IceMachineRegister.RegisterView registerView
                            && intervalMap.containsKey(registerView.getRegister())) {
                            var interval = intervalMap.get(registerView.getRegister());
                            interval.addUse(instructionId);
                            interval.addRange(blockStartId, instructionId);
                        }
                    }
                }
            }

            unhandled.addAll(intervalMap.values().stream().filter(interval -> !interval.isPrecolored()).toList());

            for (var interval : intervalMap.values().stream().filter(LiveInterval::isPrecolored).toList()) { // 为了简单起见我们把固定寄存器拆分后放入unhandled中
                for (var range : interval.livedRanges) {
                    var newInterval = new LiveInterval(interval.vreg, range.start, range.end, true);
                    newInterval.preg = interval.preg; // 预着色寄存器
                    newInterval.addRange(range.start, range.end);
                    unhandled.add(newInterval);
                    fixed.add(newInterval);
                }
            }
            unhandled.forEach(LiveInterval::updateStartEnd);
            unhandled.sort(Comparator.comparingInt(i -> ((LiveInterval) i).start)
                    .thenComparing(i -> ((LiveInterval) i).end));
            intervals.addAll(unhandled);
            registerPool.addFixedIntervals(fixed);

            for (var callPos : callPositions) {
                for (var interval : intervals) {
                    if (interval.start <= callPos && interval.end > callPos) { // 如果调用位置在区间的最后那无所谓了
                        // 如果区间包含调用位置，则标记为跨调用
                        interval.setCrossCall(true);
                        callIntervals.computeIfAbsent(callPos, _ -> new ArrayList<>()).add(interval);
                    }
                }
            }

//            Log.d("Initial live intervals: ");
//            for (var interval : intervals) {
//                Log.d("  " + interval);
//            }
        }

        private void expireOldIntervals(LiveInterval current) {
            // 过期的区间是那些结束位置小于当前区间开始位置的区间
            active.removeIf(interval -> {
                if (interval.end > current.start) { // 这里不用等于号，如果上一个区间最后一个是def，那以后用不到了可以重新分配了，如果上一个是use，那这个区间分配过来相当于直接重用了
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

            LiveInterval spillCandidate = active.stream().filter(interval -> !interval.isPrecolored())
                    .max(Comparator.comparingInt(interval -> interval.end)).orElseThrow();

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
            var all = 0;
            var spilled = 0;
            for (var interval : intervals) {
//                Log.d("LinearScanAllocator: Processing interval: " + interval.vreg + " [" + interval.start + ", " + interval.end + "], preg: " + interval.preg);
                assert interval.isValid() : "Invalid live interval: " + interval.vreg + " [" + interval.start + ", " + interval.end + "]";

                if (interval.isPrecolored()) continue; // 预着色寄存器不需要处理

                all++;
                if (interval.preg == null) {
                    spilled++;
                }

                if (interval.preg != null) {
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
            if (all != 0) {
                Log.d(String.format("分配寄存器结果: 总计: %d, 溢出: %d 溢出率: %.2f%%", all, spilled, (double) spilled / all * 100));
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
                    var insertPreInstructions = new ArrayList<IceMachineInstruction>(); // 插入到原指令前的指令
                    var insertPostInstructions = new ArrayList<IceMachineInstruction>(); // 插入到原指令后的指令

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
                                    scratchRegister = scratchRegisterPool.get(null); // 不需要指定用户
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
                                insertPreInstructions.add(load);

                                instruction.replaceOperand(operand, scratchRegisterView); // 替换原指令的虚拟寄存器操作数为溢出专用寄存器

                                // 如果目标寄存器也是这个虚拟寄存器，记录下来 以便分配同一个物理寄存器
                                if (dstReg != null && dstReg.getRegister().equals(registerView.getRegister())) {
                                    dstReplacedReg = scratchRegister.get(); // 记录被替换的寄存器
                                }
                            }
                        }
                    }

                    scratchRegisterPool.releaseAll(); // 在加载完源寄存器之后提前释放所有的scratch寄存器，这样一个寄存器就可以用于加载结果了

                    if (instruction.getOpcode().equals("BL")) {
                        // 如果是调用指令，需要保存所有活跃的caller-save
                        var callId = instructionIds.getValue(instruction);
                        // 查看此调用时所有活跃的区间
                        for (var interval : callIntervals.getOrDefault(callId, List.of())) {
                            if (interval.isPrecolored()) continue; // 预着色寄存器不需要处理
                            if (interval.preg != null
                                    && Tool.getArm64RegisterType(interval.preg) == Tool.RegisterType.CALLER_SAVED) {
                                var slot = callerSavedSlots.computeIfAbsent(interval.preg, register -> {
                                    var newSlot = machineFunction.allocateVariableStackSlot(register.getType());
                                    newSlot.setAlignment(register.getType().getByteSize());
                                    return newSlot;
                                });
                                // 生成str指令
                                var store = new ARM64Instruction("STR {src}, {local:target}" + (showDebug ? " //" + interval.vreg.getReferenceName() : ""),
                                        interval.preg.createView(interval.vreg.getType()), slot);
                                insertPreInstructions.add(store); // 在调用前插入存储指令

                                var load = new ARM64Instruction("LDR {dst}, {local:src}" + (showDebug ? " //" + interval.vreg.getReferenceName() : ""),
                                        interval.preg.createView(interval.vreg.getType()), slot);
                                insertPostInstructions.add(load);
                            }
                        }
                    }

                    if (!insertPreInstructions.isEmpty()) {
                        insertPreInstructions.forEach(instr -> instr.setParent(block));
                        block.addAll(i, insertPreInstructions); // 在原指令前插入加载指令
                        i += insertPreInstructions.size(); // 更新索引，跳过插入的加载指令
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
                                    var scratchRegister = scratchRegisterPool.get(null); // 不需要指定用户
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
                            insertPostInstructions.add(store);
//                            store.setParent(block);
//                            block.add(i + 1, store); // 在原指令后插入存储指令
//                            i++; // 跳过存储指令
                        }
                    }

                    if (!insertPostInstructions.isEmpty()) {
                        insertPostInstructions.forEach(instr -> instr.setParent(block));
                        block.addAll(i + 1, insertPostInstructions); // 在原指令前插入指令
                        i += insertPostInstructions.size(); // 更新索引，跳过插入的存储指令
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
                if (other.start <= this.start) {
                    return List.of(new Range(other.end + INSTRUCTION_ID_STEP, this.end));
                }
                
                // 如果this的结束部分与other相交，保留前面的部分
                if (other.end >= this.end) {
                    return List.of(new Range(this.start, other.start - INSTRUCTION_ID_STEP));
                }
                
                // 如果other在this中间，区间被分裂成两部分
                return List.of(
                    new Range(this.start, other.start - INSTRUCTION_ID_STEP),
                    new Range(other.end + INSTRUCTION_ID_STEP, this.end)
                );

                // 其他情况，返回原区间（理论上不应该到达这里）
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

        private boolean crossCall;
        private final List<Range> livedRanges = new ArrayList<>();

        IceMachineRegister vreg, preg;
        int start, end;
        private final Set<Integer> uses = new HashSet<>(); // 记录使用位置
        private final boolean isPrecolored; // 是否是预着色寄存器

        public LiveInterval(IceMachineRegister vreg) {
            this(vreg, -1, -1, false);
        }

        public LiveInterval(IceMachineRegister preg, boolean isPrecolored) {
            this(preg, -1, -1, isPrecolored);
            this.preg = preg;
        }

        private LiveInterval(IceMachineRegister vreg, int start, int end, boolean isPrecolored) {
            this.vreg = vreg;
            this.start = start;
            this.end = end;
            this.preg = null;
            this.isPrecolored = isPrecolored;
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

        public boolean isValid() {
            return start >= 0 && end >= start; // 确保区间有效
        }

        public boolean isPrecolored() {
            return isPrecolored;
        }

        public void setCrossCall(boolean crossCall) {
            this.crossCall = crossCall;
        }

        public boolean isCrossCall() {
            return crossCall;
        }

        @Override
        public String toString() {
            return "LiveInterval{" +
                    "livedRanges=" + livedRanges +
                    ", vreg=" + vreg +
                    ", preg=" + preg +
                    ", start=" + start +
                    ", end=" + end +
                    ", uses=" + uses +
                    ", isPrecolored=" + isPrecolored +
                    '}';
        }
    }

    public LinearScanAllocator(LivenessAnalysis.LivenessResult livenessResult) {
        this.livenessResult = livenessResult;
    }

    @Override
    public boolean run(IceMachineFunction target) {
        var registerPools = initPhysicalRegisterPool(target);

        Log.d(target.getName() + " 开始分配整数寄存器");
        var integerAllocator = new TypedLinearScanAllocator(target, registerPools.xRegPool(), registerPools.xScratchPool());
        integerAllocator.buildLiveIntervals();
        integerAllocator.linearScan();
        integerAllocator.applyRegisterAllocation(); // 应用寄存器分配结果

        Log.d(target.getName() + " 开始分配浮点寄存器");
        var floatAllocator = new TypedLinearScanAllocator(target, registerPools.vRegPool(), registerPools.vScratchPool());
        floatAllocator.buildLiveIntervals();
        floatAllocator.linearScan();
        floatAllocator.applyRegisterAllocation(); // 应用寄存器分配结果



        return true;
    }

    /**
     * 设置寄存器池
     */
    private AllRegisterPools initPhysicalRegisterPool(IceMachineFunction mf) {
        var xRegPool = new RegisterPool(List.of(
            // caller-save 寄存器
            mf.getPhysicalRegister("x0"),
            mf.getPhysicalRegister("x1"),
            mf.getPhysicalRegister("x2"),
            mf.getPhysicalRegister("x3"),
            mf.getPhysicalRegister("x4"),
            mf.getPhysicalRegister("x5"),
            mf.getPhysicalRegister("x6"),
            mf.getPhysicalRegister("x7"),
            mf.getPhysicalRegister("x8"),
            mf.getPhysicalRegister("x9"),
            mf.getPhysicalRegister("x10"),
            mf.getPhysicalRegister("x11"),
            mf.getPhysicalRegister("x12"),
            mf.getPhysicalRegister("x13"),
            mf.getPhysicalRegister("x14"),
            mf.getPhysicalRegister("x15"),
            mf.getPhysicalRegister("x16"),
            mf.getPhysicalRegister("x17"),
            mf.getPhysicalRegister("x18"),
            // callee-save 寄存器
            mf.getPhysicalRegister("x19"),
            mf.getPhysicalRegister("x20"),
            mf.getPhysicalRegister("x21"),
            mf.getPhysicalRegister("x22"),
            mf.getPhysicalRegister("x23"),
            mf.getPhysicalRegister("x24")
        ));
        var xScratchPool = new RegisterPool(List.of(
            mf.getPhysicalRegister("x25"),
            mf.getPhysicalRegister("x26"),
            mf.getPhysicalRegister("x27")
        ));

        var vRegPool = new RegisterPool(List.of(
            // caller-save 寄存器
            mf.getPhysicalRegister("v0"),
            mf.getPhysicalRegister("v1"),
            mf.getPhysicalRegister("v2"),
            mf.getPhysicalRegister("v3"),
            mf.getPhysicalRegister("v4"),
            mf.getPhysicalRegister("v5"),
            mf.getPhysicalRegister("v6"),
            mf.getPhysicalRegister("v7"),
            // callee-save 寄存器
            mf.getPhysicalRegister("v8"),
            mf.getPhysicalRegister("v9"),
            mf.getPhysicalRegister("v10"),
            mf.getPhysicalRegister("v11"),
            mf.getPhysicalRegister("v12"),
                // caller-save 寄存器
            mf.getPhysicalRegister("v16"),
            mf.getPhysicalRegister("v17"),
            mf.getPhysicalRegister("v18"),
            mf.getPhysicalRegister("v19"),
            mf.getPhysicalRegister("v20"),
            mf.getPhysicalRegister("v21"),
            mf.getPhysicalRegister("v22"),
            mf.getPhysicalRegister("v23"),
            mf.getPhysicalRegister("v24"),
            mf.getPhysicalRegister("v25"),
            mf.getPhysicalRegister("v26"),
            mf.getPhysicalRegister("v27"),
            mf.getPhysicalRegister("v28"),
            mf.getPhysicalRegister("v29"),
            mf.getPhysicalRegister("v30"),
            mf.getPhysicalRegister("v31")
        ));
        var vScratchPool = new RegisterPool(List.of(
            mf.getPhysicalRegister("v13"),
            mf.getPhysicalRegister("v14"),
            mf.getPhysicalRegister("v15")
        ));

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
