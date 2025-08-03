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
        private Map<IceBlock, LivenessAnalysis.BlockLivenessData> functionLivenessData;
        private List<IceBlock> BBs;
        private final Map<IceMachineRegister, IceStackSlot> vregSlotMap = new HashMap<>();
        private final Map<IceMachineRegister, IceMachineRegister> regMapping = new HashMap<>();

        private final List<LiveInterval> intervals = new ArrayList<>();

        private List<LiveInterval> fixed; // 固定寄存器的区间
        private final List<LiveInterval> unhandled = new ArrayList<>(); // 未处理的区间
        private final List<LiveInterval> active = new ArrayList<>(); // 活跃的区间
        private List<LiveInterval> inactive; // 当前不占用寄存器但之后仍然活跃的区间

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
                                intervalMap.get(reg).end = Math.max(intervalMap.get(reg).end, currentIndex + 10);
                            } else {
                                // 如果不存在，创建新的区间
                                intervalMap.put(reg, new LiveInterval(reg, currentIndex, currentIndex + 10));
                            }
                            intervalMap.get(reg).uses.add(currentIndex);
                        }
                    }

                    IceMachineRegister.RegisterView rv = inst.getResultReg();
                    if (rv != null && rv.getRegister().isVirtualize() && rv.getRegister().getType().equals(registerPool.getPoolType())) { // 仅处理指定类型的寄存器
                        IceMachineRegister reg = rv.getRegister();
                        if (intervalMap.containsKey(reg)) {
                            // 如果已经存在这个寄存器的区间，更新结束位置
                            intervalMap.get(reg).end = Math.max(intervalMap.get(reg).end, currentIndex + 10);
                        } else {
                            // 如果不存在，创建新的区间
                            intervalMap.put(reg, new LiveInterval(reg, currentIndex, currentIndex + 10));
                        }
                        intervalMap.get(reg).uses.add(currentIndex); // 记录使用位置
                    }

                    currentIndex += 10;
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

            unhandled.addAll(intervalMap.values());
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
         * 应用寄存器分配结果
         */
        public void applyRegisterAllocation() {
            var showDebug = Boolean.TRUE.equals(Flag.get("-fshow-trace-info"));

            for (var interval : intervals) {
                Log.d("LinearScanAllocator: Processing interval: " + interval.vreg + " [" + interval.start + ", " + interval.end + "], preg: " + interval.preg);
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

            for (var block : BBs) {
                for (int i = 0; i < block.size(); ++i) {
                    var instruction = (IceMachineInstruction)  block.get(i);
                    if (instruction instanceof IceMachineInstructionComment) continue;

                    var dstReg = instruction.getResultReg(); // 一定要在插入加载指令前获取目标寄存器因为有可能在和源操作数重合的情况下被覆盖
                    var loadSourceOperandsInstructions = new ArrayList<IceMachineInstruction>();
                    var dstReplaced = false;

                    for (var operand : List.copyOf(instruction.getSourceOperands())) {
                        if (operand instanceof IceMachineRegister.RegisterView registerView) {
                            if (regMapping.containsKey(registerView.getRegister())) {
                                // 物理寄存器
                                var preg = regMapping.get(registerView.getRegister());
                                instruction.replaceOperand(operand, preg.createView(registerView.getType()));

                                if (dstReg != null && dstReg.getRegister().equals(registerView.getRegister())) {
                                    // 如果目标寄存器也是这个虚拟寄存器，记录下来 以便分配同一个物理寄存器
                                    dstReplaced = true;
                                }
                            } else if (vregSlotMap.containsKey(registerView.getRegister())) {
                                // 溢出变量
                                var slot = vregSlotMap.get(registerView.getRegister()); // 获取对应的栈槽
                                // 先申请一个scratchRegister
                                var scratchRegister = scratchRegisterPool.get();
                                // 如果没有可用的scratchRegister，抛出异常
                                if (scratchRegister.isEmpty()) {
                                    throw new IllegalStateException("No available scratch register, Try to increase");
                                }
                                var scratchRegisterView = scratchRegister.get().createView(registerView.getType());

                                // 生成ldr指令
                                var load = new ARM64Instruction("LDR {dst}, {local:src}" + (showDebug ? " //" + registerView.getRegister().getReferenceName() : ""),
                                        scratchRegisterView, slot);
                                loadSourceOperandsInstructions.add(load);

                                instruction.replaceOperand(operand, scratchRegisterView); // 替换原指令的虚拟寄存器操作数为溢出专用寄存器

                                if (dstReg != null && dstReg.getRegister().equals(registerView.getRegister())) {
                                    // 如果目标寄存器也是这个虚拟寄存器，记录下来 以便分配同一个物理寄存器
                                    dstReplaced = true;
                                }
                            }
                        }


                    }

                    if (!loadSourceOperandsInstructions.isEmpty()) {
                        loadSourceOperandsInstructions.forEach(instr -> instr.setParent(block));
                        block.addAll(i, loadSourceOperandsInstructions); // 在原指令前插入加载指令
                        i += loadSourceOperandsInstructions.size(); // 更新索引，跳过插入的加载指令
                    }

                    if (dstReg != null && !dstReplaced) {
                        if (regMapping.containsKey(dstReg.getRegister())) {
                            // 物理寄存器
                            var preg = regMapping.get(dstReg.getRegister());
                            instruction.replaceOperand(dstReg, preg.createView(dstReg.getType()));
                        } else if (vregSlotMap.containsKey(dstReg.getRegister())) {
                            // 溢出变量
                            var slot = vregSlotMap.get(dstReg.getRegister()); // 获取对应的栈槽
                            // 先申请一个scratchRegister
                            var scratchRegister = scratchRegisterPool.get();
                            // 如果没有可用的scratchRegister，抛出异常
                            if (scratchRegister.isEmpty()) {
                                throw new IllegalStateException("No available scratch register, Try to increase");
                            }
                            var scratchRegisterView = scratchRegister.get().createView(dstReg.getType());

                            instruction.replaceOperand(dstReg, scratchRegisterView); // 替换原指令的虚拟寄存器操作数为溢出专用寄存器

                            // 生成str指令
                            var store = new ARM64Instruction("STR {src}, {local:target}" + (showDebug ? " //" + dstReg.getRegister().getReferenceName() : ""),
                                    scratchRegisterView, slot);
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
