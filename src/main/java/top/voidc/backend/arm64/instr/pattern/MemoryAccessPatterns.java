package top.voidc.backend.arm64.instr.pattern;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.backend.instr.InstructionPattern;
import top.voidc.backend.instr.InstructionSelector;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.constant.IceGlobalVariable;
import top.voidc.ir.ice.instruction.IceGEPInstruction;
import top.voidc.ir.ice.instruction.IceLoadInstruction;
import top.voidc.ir.ice.instruction.IceStoreInstruction;
import top.voidc.ir.ice.interfaces.IceMachineValue;
import top.voidc.ir.ice.type.IceArrayType;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Tool;

import static top.voidc.ir.machine.InstructionSelectUtil.*;

public class MemoryAccessPatterns {

    /**
     * 从栈槽加载值到寄存器
     */
    public static class LoadStackPattern extends InstructionPattern<IceLoadInstruction> {

        public LoadStackPattern() {
            super(10);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceLoadInstruction load) {
            // 获取源指针（应该是栈槽）
            IceValue pointer = load.getSource();
            IceMachineValue src = selector.emit(pointer);

            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(load.getType());

            // 生成加载指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "LDR {dst}, {local:src}",
                    dstReg, src
            ));

            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是栈槽
            return value instanceof IceLoadInstruction load && canBeStackSlot(selector, load.getSource());
        }
    }

    /**
     * 将值存储到栈槽
     */
    public static class StoreStackPattern extends InstructionPattern<IceStoreInstruction> {

        public StoreStackPattern() {
            super(10);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceStoreInstruction store) {
            // 获取要存储的值
            IceValue valueToStore = store.getTargetPtr();
            IceMachineValue src = selector.emit(valueToStore);

            // 获取目标指针（应该是栈槽）
            IceValue pointer = store.getValue();
            IceMachineValue dst = selector.emit(pointer);

            // 生成存储指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "STR {src}, {local:dst}",
                    src, dst
            ));

            return null; // 存储指令无返回值
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配store指令，且目标操作数是栈槽
            return value instanceof IceStoreInstruction store && canBeStackSlot(selector, store.getTargetPtr());
        }
    }

    /**
     * 从寄存器指针加载值
     */
    public static class LoadRegisterPointerPattern extends InstructionPattern<IceLoadInstruction> {

        public LoadRegisterPointerPattern() {
            super(10);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceLoadInstruction load) {
            // 获取源指针（应该在寄存器中）
            IceValue pointer = load.getSource();
            IceMachineValue src = selector.emit(pointer);

            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(load.getType());

            // 生成加载指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "LDR {dst}, [{src}]",
                    dstReg, src
            ));

            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是寄存器指针
            return value instanceof IceLoadInstruction load && canBeReg(selector, load.getSource());
        }
    }

    /**
     * 将值存储到寄存器指针
     */
    public static class StoreRegisterPointerPattern extends InstructionPattern<IceStoreInstruction> {

        public StoreRegisterPointerPattern() {
            super(10);
        }

        @Override
        public Class<?> getEmittedType() {
            return null;
        }

        @Override
        public IceMachineValue emit(InstructionSelector selector, IceStoreInstruction store) {
            // 获取目标指针
            IceValue valueToStore = store.getTargetPtr();
            IceMachineValue src = selector.emit(valueToStore);

            // 获取要存储的值
            IceValue pointer = store.getValue();
            IceMachineValue dst = selector.emit(pointer);

            // 生成存储指令
            selector.addEmittedInstruction(new ARM64Instruction(
                    "STR {x}, [{src}]",
                    dst, src
            ));

            return null; // 存储指令无返回值
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配store指令，且目标操作数是寄存器指针
            return value instanceof IceStoreInstruction store && canBeReg(selector, store.getTargetPtr());
        }
    }

    /**
     * 加载全局变量的指针地址
     */
    public static class LoadGlobalPointer extends InstructionPattern<IceGlobalVariable> {
        public LoadGlobalPointer() {
            super(2);
        }

        @Override
        public int getCost(InstructionSelector selector, IceGlobalVariable value) {
            return getIntrinsicCost();
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceGlobalVariable variable) {
            // 获取源指针（应该是全局变量）
            // 创建目标寄存器
            IceMachineFunction mf = selector.getMachineFunction();
            var dstReg = mf.allocateVirtualRegister(variable.getType());

            selector.addEmittedInstruction(new ARM64Instruction("ADRP {dst}, " + variable.getName(), dstReg));
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {addr}, :lo12:" + variable.getName(), dstReg, dstReg));
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            // 匹配load指令，且源操作数是全局常量
            return value instanceof IceGlobalVariable;
        }
    }

    /**
     * GEP指令的通用基类，处理共同的偏移量计算逻辑
     */
    public abstract static class AbstractGEPLoadPattern extends InstructionPattern<IceGEPInstruction> {
        protected AbstractGEPLoadPattern(int intrinsicCost) {
            super(intrinsicCost);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceGEPInstruction gep) {
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(gep.getType());
            // 计算偏移量 先计算相对基地址的*元素*个数，最后乘以元素大小
            IceConstantData accumulatedOffset = IceConstantData.create(0);

            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, #0", dstReg));

            IceType currentType = ((IcePtrType<?>) gep.getBasePtr().getType()).getPointTo();
            IceType insideType;
            int currentIndexArraySize;

            if (currentType instanceof IceArrayType currentArrayType) {
                currentIndexArraySize = currentArrayType.getTotalSize();
                insideType = currentArrayType.getInsideElementType();
            } else {
                currentIndexArraySize = 1;
                insideType = currentType;
            }

            assert !insideType.isArray() && !insideType.isPointer();
            for (var i = 0; i < gep.getIndices().size(); i++) {
                var currentIndexValue = gep.getIndices().get(i);
                if (currentIndexValue instanceof IceConstantInt constIndex) {
                    var offset = constIndex.multiply(IceConstantData.create(currentIndexArraySize));
                    accumulatedOffset = accumulatedOffset.plus(offset);
                } else {
                    if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                        accumulateConstantOffset(selector, dstReg, accumulatedOffset);
                        accumulatedOffset = IceConstantData.create(0);
                    }

                    var indexReg = (IceMachineRegister.RegisterView) selector.emit(currentIndexValue);
                    if (currentIndexArraySize == 1) {
                        if (!indexReg.getType().equals(IceType.I64)) {
                            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}, uxtw",
                                dstReg, dstReg, indexReg));
                        } else {
                            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}",
                                dstReg, dstReg, indexReg));
                        }
                    } else {
                        assert currentIndexArraySize > 1;
                        if (Tool.isPowerOfTwo(currentIndexArraySize)) {
                            if (indexReg.getType().equals(IceType.I64)) {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}, lsl {imm:size}",
                                    dstReg, dstReg, indexReg, IceConstantData.create(Tool.log2(currentIndexArraySize))));
                            } else {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}, uxtw {imm:size}",
                                    dstReg, dstReg, indexReg, IceConstantData.create(Tool.log2(currentIndexArraySize))));
                            }
                        } else {
                            var currentArraySizeValue = IceConstantData.create((long) currentIndexArraySize);
                            selector.select(currentArraySizeValue);
                            var sizeReg = (IceMachineRegister.RegisterView) selector.emit(currentArraySizeValue);
                            assert sizeReg.getType().equals(IceType.I64);

                            if (indexReg.getType().equals(IceType.I64)) {
                                selector.addEmittedInstruction(new ARM64Instruction("MADD {dst}, {index}, {size}, {base}",
                                    dstReg, indexReg, sizeReg, dstReg));
                            } else {
                                var indexReg64 = indexReg.getRegister().createView(IceType.I64);
                                selector.addEmittedInstruction(new ARM64Instruction("UXTW {dst}, {orign}",
                                    indexReg64, indexReg));
                                selector.addEmittedInstruction(new ARM64Instruction("MADD {dst}, {size}, {index}, {base}",
                                    dstReg, indexReg64, sizeReg, dstReg));
                            }
                        }
                    }
                }

                if (currentType instanceof IceArrayType arrayType) {
                    currentIndexArraySize = arrayType.getElementType() instanceof IceArrayType innerArrayType ? 
                        innerArrayType.getTotalSize() : 1;
                    if (arrayType.getElementType() instanceof IceArrayType) {
                        currentType = arrayType.getElementType();
                    }
                }
            }

            if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                accumulateConstantOffset(selector, dstReg, accumulatedOffset);
            }

            if (Tool.isPowerOfTwo(insideType.getByteSize())) {
                var lsl = Tool.log2(insideType.getByteSize());
                selector.addEmittedInstruction(new ARM64Instruction("LSL {dst}, {base}, {imm:size}",
                    dstReg, dstReg, IceConstantData.create(lsl)));
            } else {
                var insideTypeSize = IceConstantData.create(insideType.getByteSize());
                selector.select(insideTypeSize);
                var imm = selector.emit(insideTypeSize);
                selector.addEmittedInstruction(new ARM64Instruction("MUL {dst}, {base}, {imm}",
                    dstReg, dstReg, imm));
            }

            // 获取基地址并加到结果中
            var basePtrValue = getBasePointer(selector, gep);
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}",
                dstReg, basePtrValue, dstReg));
            return dstReg;
        }

        private void accumulateConstantOffset(InstructionSelector selector, IceMachineRegister.RegisterView dstReg, IceConstantData accumulatedOffset) {
            if (isImm12(accumulatedOffset)) {
                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {imm12:offset}",
                    dstReg, dstReg, accumulatedOffset));
            } else {
                selector.select(accumulatedOffset);
                var offsetReg = selector.emit(accumulatedOffset);
                if (!offsetReg.getType().equals(IceType.I64)) {
                    selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}, uxtw",
                        dstReg, dstReg, offsetReg));
                } else {
                    selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}",
                        dstReg, dstReg, offsetReg));
                }
            }
        }

        protected abstract IceMachineValue getBasePointer(InstructionSelector selector, IceGEPInstruction gep);

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (!(value instanceof IceGEPInstruction gep)) {
                return false;
            }

            for (var index : gep.getIndices()) {
                if (!(index instanceof IceConstantInt) && !canBeReg(selector, index)) {
                    return false;
                }
            }

            return testBasePointer(selector, gep);
        }

        protected abstract boolean testBasePointer(InstructionSelector selector, IceGEPInstruction gep);
    }

    /**
     * 处理全局变量的GEP指令
     */
    public static class GEPLoadGlobalPointer extends AbstractGEPLoadPattern {
        public GEPLoadGlobalPointer() {
            super(0);
        }

        @Override
        protected IceMachineValue getBasePointer(InstructionSelector selector, IceGEPInstruction gep) {
            return selector.emit(gep.getBasePtr());
        }

        @Override
        protected boolean testBasePointer(InstructionSelector selector, IceGEPInstruction gep) {
            return gep.getBasePtr() instanceof IceGlobalVariable;
        }
    }

    /**
     * 处理局部变量的GEP指令
     */
    public static class GEPLoadLocalPointer extends AbstractGEPLoadPattern {
        public GEPLoadLocalPointer() {
            super(0);
        }

        @Override
        protected IceMachineValue getBasePointer(InstructionSelector selector, IceGEPInstruction gep) {
            var slot = (IceStackSlot) selector.emit(gep.getBasePtr());
            var basePtrReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I64);
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, sp, {local-offset:slot}",
                basePtrReg, slot));
            return basePtrReg;
        }

        @Override
        protected boolean testBasePointer(InstructionSelector selector, IceGEPInstruction gep) {
            return canBeStackSlot(selector, gep.getBasePtr());
        }
    }

    /**
     * 处理函数参数的GEP指令
     */
    public static class GEPLoadArgumentPointer extends AbstractGEPLoadPattern {
        public GEPLoadArgumentPointer() {
            super(10);
        }

        @Override
        protected IceMachineValue getBasePointer(InstructionSelector selector, IceGEPInstruction gep) {
            return selector.emit(gep.getBasePtr());
        }

        @Override
        protected boolean testBasePointer(InstructionSelector selector, IceGEPInstruction gep) {
            return gep.getBasePtr() instanceof IceFunction.IceFunctionParameter;
        }
    }
}
