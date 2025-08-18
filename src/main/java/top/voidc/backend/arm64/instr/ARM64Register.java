package top.voidc.backend.arm64.instr;

import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.ice.type.IceVecType;
import top.voidc.ir.machine.IceMachineRegister;

public class ARM64Register extends IceMachineRegister {
    public ARM64Register(String name, IceType type) {
        super(name, type);
    }

    public ARM64Register(String name, IceType type, boolean isVirtualize) {
        super(name, type, isVirtualize);
    }


    @Override
    public RegisterView createView(IceType type) {
        if ((getType().isInteger() && type.isFloat()) || (getType().isVector() && type.isInteger())) {
            throw new IllegalArgumentException("Cannot create a view with different type this: " + getType() + " want:  " + type);
        }

        // TODO 这里最好按照不同的寄存器类型来创建不同的 RegisterView 分开
        var registerPrefix = switch (type.getTypeEnum()) {
            case I8, I32 -> "w";
            case I64, PTR -> "x";
            case F32 -> "s";
            case F64 -> "d";
            case VEC -> {
                assert type.getByteSize() == 16 : "ARM64 只有 128 位的向量寄存器";
                yield "q";
            }
            default -> throw new IllegalStateException();
        };
        var registerSuffix = switch (type.getTypeEnum()) {
            case VEC -> {
                var vecType = (IceVecType) type;
                assert type.getByteSize() == 16 : "ARM64 只有 128 位的向量寄存器";
                yield switch (vecType.getScalarType().getTypeEnum()) {
                    case I64, F64 -> ".2d";
                    case I32, F32 -> ".4s";
                    case I8 -> ".16b";
                    case ANY -> "";
                    default -> throw new IllegalStateException();
                };
            }
            default -> "";
        };
        return new RegisterView(this, (isVirtualize() ? "virt_" : "") + registerPrefix + getName() + registerSuffix, type);
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
