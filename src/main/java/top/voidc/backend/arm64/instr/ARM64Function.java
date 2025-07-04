package top.voidc.backend.arm64.instr;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineRegister;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class ARM64Function extends IceMachineFunction {

    public ARM64Function(IceFunction function) {
        super(function.getName());
        initParameters(function);
    }

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
                        var xReg = allocatePhysicalRegister("x" + intParamReg.get(), IceType.F64);
                        bindPhysicalRegisterToValue(parameter, xReg);
                        intParamReg.getAndIncrement();
                    } else {
                        // TODO: emit一个store在prologue里面
                    }
                }
                case I32 -> {
                    if (intParamReg.get() < 8) {
                        var wReg = allocatePhysicalRegister("w" + intParamReg.get(), IceType.I64);
                        bindPhysicalRegisterToValue(parameter, wReg);
                        intParamReg.getAndIncrement();
                    }

                }
                case F32 -> {
                    if (floatParamReg.get() < 8) {
                        var vReg = allocatePhysicalRegister("s" + floatParamReg.get(), IceType.F32);
                        bindPhysicalRegisterToValue(parameter, vReg);
                        floatParamReg.getAndIncrement();
                    }
                }
            }
        });

    }

    // IceValue和存放虚拟寄存器的关系
    private final Map<IceValue, IceMachineRegister> valueToRegMap = new HashMap<>();

    private final Map<String, IceMachineRegister> physicalRegisters = new HashMap<>();

    private final Map<String, IceMachineRegister> virtualRegisters = new HashMap<>();

    @Override
    public void bindVirtualRegisterToValue(IceValue value, IceMachineRegister register) {
        if (!virtualRegisters.containsKey(register.getName())) {
            throw new IllegalArgumentException("Wrong use!");
        }
        valueToRegMap.put(value, register);
    }

    @Override
    public void bindPhysicalRegisterToValue(IceValue value, IceMachineRegister register) {
        if (!physicalRegisters.containsKey(register.getName())) {
            throw new IllegalArgumentException("Wrong use!");
        }
        valueToRegMap.put(value, register);
    }

    @Override
    public IceMachineRegister getRegisterForValue(IceValue value) {
        return valueToRegMap.get(value);
    }

    @Override
    public IceMachineRegister allocatePhysicalRegister(String name, IceType type) {
        return physicalRegisters.computeIfAbsent(name, _ -> new ARM64Register(name, type, false));
    }

    @Override
    public IceMachineRegister allocateVirtualRegister(String name, IceType type) {
        return virtualRegisters.computeIfAbsent(name, _ -> new ARM64Register(name, type));
    }

    private int integerRegCount = 0;
    private float floatRegCount = 0;

    @Override
    public IceMachineRegister allocateVirtualRegister(IceType type) {
        return switch (type.getTypeEnum()) {
            case I32 -> allocateVirtualRegister("virt_w" + integerRegCount++, IceType.I32);
            case F64 ->  allocateVirtualRegister("virt_x" + integerRegCount++, IceType.F64);
            case F32 -> allocateVirtualRegister("virt_s" + floatRegCount++, IceType.F32);
            default -> throw new IllegalArgumentException("Wrong type!");
        };
    }

    @Override
    public IceMachineRegister getReturnRegister(IceType type) {
        return switch (type.getTypeEnum()) {
            case I32 -> allocatePhysicalRegister("w0", IceType.I32);
            case I64 -> allocateVirtualRegister("x0", IceType.I64);
            case F32 -> allocatePhysicalRegister("s0", IceType.F32);
            default -> throw new IllegalArgumentException("Wrong type!");
        };
    }

    @Override
    public String getArchitecture() {
        return "aarch64";
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
