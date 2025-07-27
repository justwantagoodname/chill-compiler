package top.voidc.backend.arm64.instr;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceCallInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineBlock;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Tool;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

public class ARM64Function extends IceMachineFunction {

    public ARM64Function(IceFunction function) {
        super(function.getName());
        setReturnType(function.getReturnType());
        initMachineBlocks(function);
        setEntryBlock(getMachineBlock("entry"));
        initParameters(function);

    }

    // IceValue 和存放寄存器(视图)的关系
    private final Map<IceValue, IceMachineValue> valueToMachineValue = new HashMap<>();

    // 所有分配出去的物理寄存器
    private final Map<String, IceMachineRegister> physicalRegisters = new HashMap<>();

    // 所有分配出去的虚拟寄存器
    private final Map<String, IceMachineRegister> virtualRegisters = new HashMap<>();

    // 所有机器指令块
    private final Map<String, IceMachineBlock> machineBlocks = new HashMap<>();

    private final IceMachineRegister zeroRegister = allocatePhysicalRegister("zr", IceType.I64);

    private final IceMachineRegister returnRegister = allocatePhysicalRegister("0", IceType.I64);

    private final List<IceStackSlot> stackFrame = new ArrayList<>();

    /**
     * 根据 AAPCS64 生成函数参数
     */
    private void initParameters(IceFunction function) {
        var parameters = function.getParameters();
        final AtomicInteger intParamReg = new AtomicInteger(); // x0 - x7
        AtomicInteger floatParamReg = new AtomicInteger(); // s0 - s7

        parameters.forEach(parameter -> {
            switch (parameter.getType().getTypeEnum()) {
                case ARRAY, PTR -> {
                    if (intParamReg.get() < 8) {
                        var reg = allocatePhysicalRegister(String.valueOf(intParamReg.get()), IceType.I64).createView(IceType.I64);
                        var vreg = allocateVirtualRegister(IceType.I64);
                        getEntryBlock().addInstruction(new ARM64Instruction("MOV {dst}, {src}", vreg, reg));
                        bindVirtualRegisterToValue(parameter, vreg);
                        intParamReg.getAndIncrement();
                    } else {
                        Tool.TODO("ARM64Function.initParameters: 处理参数超过8个的情况");
                        // TODO: emit一个store在prologue里面
                    }
                }
                case I32 -> {
                    if (intParamReg.get() < 8) {
                        var reg = allocatePhysicalRegister(String.valueOf(intParamReg.get()), IceType.I64).createView(IceType.I32);
                        var vreg = allocateVirtualRegister(IceType.I32);
                        getEntryBlock().addInstruction(new ARM64Instruction("MOV {dst}, {src}", vreg, reg));
                        bindVirtualRegisterToValue(parameter, vreg);
                        intParamReg.getAndIncrement();
                    } else {
                        Tool.TODO("ARM64Function.initParameters: 处理参数超过8个的情况");
                    }
                }
                case F32 -> {
                    if (floatParamReg.get() < 8) {
                        throw new UnsupportedOperationException();
                    }
                }
            }
        });

    }

    private void initMachineBlocks(IceFunction function) {
        AtomicInteger blockCount = new AtomicInteger();
        function.blocks()
                .forEach(block -> {
                    var machineBlock = new IceMachineBlock(this, ".L" + getName() + "_" + blockCount);
                    blockCount.getAndIncrement();
                    machineBlocks.put(block.getName(), machineBlock);
                });
    }

    @Override
    public List<IceStackSlot> getStackFrame() {
        return stackFrame;
    }

    @Override
    public IceStackSlot.VariableStackSlot allocateVariableStackSlot(IceType type) {
        var slot = new IceStackSlot.VariableStackSlot(this, type);
        stackFrame.add(slot);
        return slot;
    }

    @Override
    public IceStackSlot.ArgumentStackSlot allocateArgumentStackSlot(IceCallInstruction callInstruction, int argumentIndex, IceType type) {
        var slot = new IceStackSlot.ArgumentStackSlot(this, callInstruction, argumentIndex, type);
        stackFrame.add(slot);
        return slot;
    }

    @Override
    public void bindMachineValueToValue(IceValue value, IceMachineValue machineValue) {
        valueToMachineValue.put(value, machineValue);
    }

    @Override
    public void bindVirtualRegisterToValue(IceValue value, IceMachineRegister.RegisterView view) {
        if (!virtualRegisters.containsKey(view.getRegister().getName())) {
            throw new IllegalArgumentException("Wrong use!");
        }
        valueToMachineValue.put(value, view);
    }

    @Override
    public void bindPhysicalRegisterToValue(IceValue value, IceMachineRegister.RegisterView view) {
        if (!physicalRegisters.containsKey(view.getRegister().getName())) {
            throw new IllegalArgumentException("Wrong use!");
        }
        valueToMachineValue.put(value, view);
    }

    @Override
    public Optional<IceMachineValue> getRegisterForValue(IceValue value) {
        return Optional.ofNullable(valueToMachineValue.get(value));
    }

    @Override
    protected IceMachineRegister allocatePhysicalRegister(String name, IceType type) {
        return physicalRegisters.computeIfAbsent(name, _ -> new ARM64Register(name, type, false));
    }

    @Override
    public IceMachineRegister getPhysicalRegister(String name) {
        Objects.requireNonNull(name);

        IceType registerType = switch (name.substring(0, 1).toLowerCase()) {
            case "x" -> IceType.I64;
            default -> throw new IllegalArgumentException("Wrong use!");
        };

        assert !name.substring(1).isBlank();
        return allocatePhysicalRegister(name.substring(1), registerType);
    }

    @Override
    protected IceMachineRegister allocateVirtualRegister(String name, IceType type) {
        return virtualRegisters.computeIfAbsent(name, _ -> new ARM64Register(name, type));
    }

    private int integerRegCount = 0;
    private float floatRegCount = 0;

    @Override
    public IceMachineRegister.RegisterView allocateVirtualRegister(IceType type) {
        return switch (type.getTypeEnum()) {
            case I32, I64, PTR -> allocateVirtualRegister(String.valueOf(integerRegCount++), IceType.I64).createView(type);
            default -> throw new IllegalArgumentException("Wrong type!");
        };
    }

    @Override
    public IceMachineRegister.RegisterView getReturnRegister(IceType type) {
        // TODO: Fix this. 需要返回固定的内存地址，根据浮点类型返回对应寄存器
        return returnRegister.createView(type);
    }

    @Override
    public IceMachineRegister.RegisterView getZeroRegister(IceType type) {
        // TODO: 根据浮点类型返回对应的零寄存器
        return zeroRegister.createView(type);
    }

    @Override
    public Set<IceMachineRegister> getAllRegisters() {
        var allRegisters = new HashSet<IceMachineRegister>();
        allRegisters.addAll(physicalRegisters.values());
        allRegisters.addAll(virtualRegisters.values());
        return Set.copyOf(allRegisters); // 返回一个不可修改的集合
    }

    @Override
    public IceMachineBlock getMachineBlock(String name) {
        var block = machineBlocks.get(name);
        if (block == null) throw new IllegalArgumentException("Block not found: " + name);
        return block;
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

    @Override
    public void getTextIR(StringBuilder builder) {
        // === 生成描述函数的伪指令 ===
        builder.append("\t.global ").append(getName()).append("\n")
                .append("\t.type ").append(getName()).append(", %function\n")
                .append("\t.align ").append(Tool.log2(getAlignment())).append("\n")
                .append(getName()).append(":\n");
        blocks().forEach(block -> {
            block.getTextIR(builder);
            builder.append("\n");
        });
    }
}
