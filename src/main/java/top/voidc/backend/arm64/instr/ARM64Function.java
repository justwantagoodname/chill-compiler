package top.voidc.backend.arm64.instr;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.instruction.IceCallInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceVecType;
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
    }

    /**
     * 根据 AAPCS64 生成函数参数
     */
    @Override
    public void initParameters(Map<IceValue, IceMachineValue> machineValueMap, IceFunction function) {
        var parameters = function.getParameters();
        int intParamReg = 0; // x0 - x7
        int floatParamReg = 0; // s0 - s7

        for (int i = 0; i < parameters.size(); i++) {
            var parameter = parameters.get(i);
            switch (parameter.getType().getTypeEnum()) {
                case ARRAY, PTR, I32 -> {
                    if (intParamReg < 8) {
                        var reg = getPhysicalRegister("x" + intParamReg).createView(parameter.getType());
                        var vreg = allocateVirtualRegister(parameter.getType());
                        getEntryBlock().addInstruction(new ARM64Instruction("MOV {dst}, {src}", vreg, reg));
                        machineValueMap.put(parameter, vreg);
                        intParamReg++;
                    } else {
                        var slot = allocateParameterStackSlot(i, parameter.getType());
                        var vreg = allocateVirtualRegister(parameter.getType());
                        // 将参数从栈中加载到虚拟寄存器
                        getEntryBlock().addInstruction(new ARM64Instruction("LDR {dst}, {local:src}", vreg, slot));
                        machineValueMap.put(parameter, vreg);
                    }
                }
                case F32, F64 -> {
                    if (floatParamReg < 8) {
                        var reg = getPhysicalRegister("v" + floatParamReg).createView(parameter.getType());
                        var vreg = allocateVirtualRegister(parameter.getType());
                        getEntryBlock().addInstruction(new ARM64Instruction("FMOV {dst}, {src}", vreg, reg));
                        machineValueMap.put(parameter, vreg);
                        floatParamReg++;
                    } else {
                        var slot = allocateParameterStackSlot(i, parameter.getType());
                        var vreg = allocateVirtualRegister(parameter.getType());
                        // 将参数从栈中加载到虚拟寄存器
                        getEntryBlock().addInstruction(new ARM64Instruction("LDR {dst}, {local:src}", vreg, slot));
                        machineValueMap.put(parameter, vreg);
                    }
                }
            }
        }
    }

    // 所有分配出去的物理寄存器
    private final Map<String, IceMachineRegister> physicalRegisters = new HashMap<>();

    // 所有分配出去的虚拟寄存器
    private final Map<String, IceMachineRegister> virtualRegisters = new HashMap<>();

    // 所有机器指令块
    private final Map<String, IceMachineBlock> machineBlocks = new HashMap<>();

    private int integerVRegCount = 0;

    private int floatVRegCount = 0;

    private final IceMachineRegister zeroRegister = allocatePhysicalRegister("zr", IceType.I64);

    private final IceMachineRegister returnRegister = allocatePhysicalRegister("0", IceType.I64);

    private final IceMachineRegister floatReturnRegister = allocatePhysicalRegister("0", IceVecType.VEC128);

    private final List<IceStackSlot> stackFrame = new ArrayList<>();

    private void initMachineBlocks(IceFunction function) {
        AtomicInteger blockCount = new AtomicInteger();
        function.blocks()
                .forEach(block -> {
                    var machineBlock = new IceMachineBlock(this, ".L" + getName() + "_" + block.getName() + "_" + blockCount);
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
    public IceStackSlot.ParameterStackSlot allocateParameterStackSlot(int parameterIndex, IceType type) {
        var slot = new IceStackSlot.ParameterStackSlot(this, parameterIndex, type);
        stackFrame.add(slot);
        return slot;
    }

    @Override
    public IceStackSlot.SavedRegisterStackSlot allocateSavedRegisterStackSlot(IceMachineRegister register) {
        Objects.requireNonNull(register);
        if (!physicalRegisters.containsValue(register)) {
            throw new IllegalArgumentException("Register " + register.getName() + " is not a physical register.");
        }
        var slot = new IceStackSlot.SavedRegisterStackSlot(this, register);
        slot.setAlignment(Math.max(register.getBitwidth() / 8, 4)); // 最少按照 4 字节对齐
        stackFrame.add(slot);
        return slot;
    }

    @Override
    protected IceMachineRegister allocatePhysicalRegister(String name, IceType type) {
        return physicalRegisters.computeIfAbsent(type + "|" + name, _ -> new ARM64Register(name, type, false));
    }

    @Override
    public IceMachineRegister getPhysicalRegister(String name) {
        Objects.requireNonNull(name);

        IceType registerType = switch (name.substring(0, 1).toLowerCase()) {
            case "x" -> IceType.I64;
            case "v" -> IceVecType.VEC128;
            default -> throw new IllegalArgumentException("Wrong use!");
        };

        assert !name.substring(1).isBlank();
        return allocatePhysicalRegister(name.substring(1), registerType);
    }

    public Set<IceMachineRegister> getAllVirtualRegisters() {
        return Set.copyOf(virtualRegisters.values());
    }

    @Override
    protected IceMachineRegister allocateVirtualRegister(String name, IceType type) {
        return virtualRegisters.computeIfAbsent(type + "|" + name, _ -> new ARM64Register(name, type));
    }

    @Override
    public IceMachineRegister.RegisterView allocateVirtualRegister(IceType type) {
        return switch (type.getTypeEnum()) {
            case I32, I64, PTR -> allocateVirtualRegister(String.valueOf(integerVRegCount++), IceType.I64).createView(type);
            case F32, F64 -> allocateVirtualRegister(String.valueOf(floatVRegCount++), type).createView(type);
            default -> throw new IllegalArgumentException("Wrong type!");
        };
    }

    @Override
    public IceMachineRegister.RegisterView getReturnRegister(IceType type) {
        if (type.isInteger()) {
            return returnRegister.createView(type);
        } else if (type.isFloat()) {
            return floatReturnRegister.createView(type);
        }
        throw new IllegalArgumentException("Wrong type!");
    }

    @Override
    public IceMachineRegister.RegisterView getZeroRegister(IceType type) {
        // TODO: 根据浮点类型返回对应的零寄存器
        return zeroRegister.createView(type);
    }

    @Override
    public Collection<IceMachineRegister> getAllRegisters() {
        var allRegisters = new ArrayList<IceMachineRegister>();
        allRegisters.addAll(physicalRegisters.values());
        allRegisters.addAll(virtualRegisters.values());
        return List.copyOf(allRegisters); // 返回一个不可修改的集合
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
    public int getArchitectureBitSize() {
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
