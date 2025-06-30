package top.voidc.ir.ice.instruction;

import top.voidc.ir.IceBlock;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.misc.Log;

import java.util.List;

public class IceGEPInstruction extends IceInstruction {
    private boolean isInBounds = false;

    public IceGEPInstruction(IceBlock block, IceValue basePtr, List<IceValue> indices) {
        super(block, getReturnType(basePtr, indices));
        Log.should(basePtr.getType().isPointer(), "GEP指令的基址必须是指针类型");
        this.addOperand(basePtr);
        indices.forEach(this::addOperand);
    }

    public IceGEPInstruction(IceBlock block, String name, IceValue basePtr, List<IceValue> indices) {
        super(block, name, getReturnType(basePtr, indices));
        Log.should(basePtr.getType().isPointer(), "GEP指令的基址必须是指针类型");
        this.addOperand(basePtr);
        indices.forEach(this::addOperand);
    }

    /**
     * 通过给定指针和索引计算返回类型
     * @param basePtr
     * @param indices
     * @return
     */
    private static IceType getReturnType(IceValue basePtr, List<IceValue> indices) {
        final var baseType = (IcePtrType<?>) basePtr.getType();
        final var accessIndices = indices.subList(1, indices.size()); // 第一个索引是自身偏移所以删除
        if (accessIndices.isEmpty()) {
            return baseType;
        }

        var currentType = baseType.getPointTo();
        for (var i = 0; i < accessIndices.size(); i++) {
            if (currentType instanceof IcePtrType<?> ptrType) {
                currentType = ptrType.getPointTo();
            } else if (currentType instanceof IceArrayType arrayType) {
                currentType = arrayType.getElementType();
            } else {
                throw new IllegalStateException("访问列表和指针类型不匹配");
            }
        }

        return new IcePtrType<>(currentType);
    }

    public IceValue getBasePtr() {
        return this.getOperand(0);
    }

    public boolean isInBounds() {
        return isInBounds;
    }

    public void setInBounds(boolean inBounds) {
        isInBounds = inBounds;
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("%").append(getName()).append(" = getelementptr");
        if (isInBounds) {
            builder.append(" inbounds");
        }
        builder.append(" ").append(((IcePtrType<?>) getBasePtr().getType()).getPointTo())
                .append(", ").append(getBasePtr().getReferenceName());
        
        for (int i = 1; i < getOperandsList().size(); i++) {
            builder.append(", ").append(getOperand(i).getReferenceName());
        }
    }
}
