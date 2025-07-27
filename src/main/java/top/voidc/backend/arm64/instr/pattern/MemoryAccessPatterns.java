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
     * 加载GEP变成为指针寄存器
     */
    public static class GEPLoadGlobalPointer extends InstructionPattern<IceGEPInstruction> {
        public GEPLoadGlobalPointer() {
            super(0);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceGEPInstruction gep) {
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(gep.getType());
            // 计算偏移量 先计算相对基地址的*元素*个数，最后乘以元素大小
            IceConstantData accumulatedOffset = IceConstantData.create(0); // 累计起来的偏移量 如果出现动态的下表就清空累加到里面

            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, #0", dstReg)); // 清空偏移寄存器

            IceType currentType = ((IcePtrType<?>) gep.getBasePtr().getType()).getPointTo();
            // 当前一个元素代表的的大小
            assert currentType instanceof IceArrayType;
            IceArrayType currentArrayType = (IceArrayType) currentType;
            var currentIndexArraySize = currentArrayType.getTotalSize();
            var insideType = ((IceArrayType) currentType).getInsideElementType(); // 最内的元素类型

            assert !insideType.isArray() && !insideType.isPointer();
            for (var i = 0; i < gep.getIndices().size(); i++) {
                var currentIndexValue = gep.getIndices().get(i);
                if (currentIndexValue instanceof IceConstantInt constIndex) {
                    // 这个下标是编译时常量
                    var offset = constIndex.multiply(IceConstantData.create(currentIndexArraySize));
                    accumulatedOffset = accumulatedOffset.plus(offset);
                } else {
                    // 这个下标是动态的
                    if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                        // 如果之前有累加的偏移量
                        if (isImm12(accumulatedOffset)) {
                            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {imm12:offset}", dstReg, dstReg, accumulatedOffset));
                        } else {
                            selector.select(accumulatedOffset);
                            var offsetReg = (IceMachineRegister.RegisterView) selector.emit(accumulatedOffset);
                            if (!offsetReg.getType().equals(IceType.I64)) {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}, uxtw", dstReg, dstReg, offsetReg));
                            } else {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, dstReg, offsetReg));
                            }
                        }

                        accumulatedOffset = IceConstantData.create(0); // 清空累加
                    }
                    // 生成加入动态下标的指令
                    var indexReg = (IceMachineRegister.RegisterView) selector.emit(currentIndexValue);
                    // 先乘内部数组的大小
                    if (currentIndexArraySize > 1) {
                        var currentArraySizeValue = IceConstantData.create(currentIndexArraySize);
                        selector.select(currentArraySizeValue);
                        var sizeReg = selector.emit(currentArraySizeValue);
                        selector.addEmittedInstruction(new ARM64Instruction("MUL {index}, {index}, {size}", indexReg, indexReg, sizeReg));
                    }
                    // 单个大小 等于乘 1
                    // 累加到偏移寄存器
                    if (!indexReg.getType().equals(IceType.I64)) {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}, uxtw", dstReg, dstReg, indexReg));
                    } else {
                        // 如果是64位寄存器
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}", dstReg, dstReg, indexReg));
                    }
                }


                currentIndexArraySize = // 当前一个元素代表的的大小
                        ((IceArrayType) currentType).getElementType() instanceof IceArrayType innerArrayType ? innerArrayType.getTotalSize() : 1;
                if (((IceArrayType) currentType).getElementType() instanceof IceArrayType) {
                    currentType = ((IceArrayType) currentType).getElementType();
                }
            }

            if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                // 如果之前有累加的偏移量
                if (isImm12(accumulatedOffset)) {
                    selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {imm12:offset}", dstReg, dstReg, accumulatedOffset));
                } else {
                    selector.select(accumulatedOffset);
                    var offsetReg = selector.emit(accumulatedOffset);
                    if (!offsetReg.getType().equals(IceType.I64)) {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}, uxtw", dstReg, dstReg, offsetReg));
                    } else {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, dstReg, offsetReg));
                    }
                }

                accumulatedOffset = IceConstantData.create(0); // 清空累加
            }

            assert accumulatedOffset.equals(IceConstantData.create(0));

            // 元素个数乘以元素的bytesize
            if (Tool.isPowerOfTwo(insideType.getByteSize())) {
                var lsl = Tool.log2(insideType.getByteSize());
                selector.addEmittedInstruction(new ARM64Instruction("LSL {dst}, {base}, {imm16:size}", dstReg, dstReg, IceConstantData.create(lsl)));
            } else {
                var insideTypeSize = IceConstantData.create(insideType.getByteSize());
                selector.select(insideTypeSize);
                var imm = selector.emit(insideTypeSize);
                selector.addEmittedInstruction(new ARM64Instruction("MUL {dst}, {base}, {imm}", dstReg, dstReg, imm));
            }

            // 获取全局变量
            var basePtrReg = (IceMachineRegister.RegisterView) selector.emit(gep.getBasePtr());

            // 如果有累加的偏移量
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, basePtrReg, dstReg));
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (!(value instanceof IceGEPInstruction gep)) {
                return false;
            }

            if (!(gep.getBasePtr() instanceof IceGlobalVariable)) return false;

            for (var index : gep.getIndices()) {
                if (!(index instanceof IceConstantInt) && !canBeReg(selector, index)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class GEPLoadLocalPointer extends InstructionPattern<IceGEPInstruction> {
        public GEPLoadLocalPointer() {
            super(0);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceGEPInstruction gep) {
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(gep.getType());
            // 计算偏移量 先计算相对基地址的*元素*个数，最后乘以元素大小
            IceConstantData accumulatedOffset = IceConstantData.create(0); // 累计起来的偏移量 如果出现动态的下表就清空累加到里面

            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, #0", dstReg)); // 清空偏移寄存器

            IceType currentType = ((IcePtrType<?>) gep.getBasePtr().getType()).getPointTo();
            // 当前一个元素代表的的大小
            assert currentType instanceof IceArrayType;
            IceArrayType currentArrayType = (IceArrayType) currentType;
            var currentIndexArraySize = currentArrayType.getTotalSize();
            var insideType = ((IceArrayType) currentType).getInsideElementType(); // 最内的元素类型

            assert !insideType.isArray() && !insideType.isPointer();
            for (var i = 0; i < gep.getIndices().size(); i++) {
                var currentIndexValue = gep.getIndices().get(i);
                if (currentIndexValue instanceof IceConstantInt constIndex) {
                    // 这个下标是编译时常量
                    var offset = constIndex.multiply(IceConstantData.create(currentIndexArraySize));
                    accumulatedOffset = accumulatedOffset.plus(offset);
                } else {
                    // 这个下标是动态的
                    if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                        // 如果之前有累加的偏移量
                        if (isImm12(accumulatedOffset)) {
                            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {imm12:offset}", dstReg, dstReg, accumulatedOffset));
                        } else {
                            selector.select(accumulatedOffset);
                            var offsetReg = (IceMachineRegister.RegisterView) selector.emit(accumulatedOffset);
                            if (!offsetReg.getType().equals(IceType.I64)) {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}, uxtw", dstReg, dstReg, offsetReg));
                            } else {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, dstReg, offsetReg));
                            }
                        }

                        accumulatedOffset = IceConstantData.create(0); // 清空累加
                    }
                    // 生成加入动态下标的指令
                    var indexReg = (IceMachineRegister.RegisterView) selector.emit(currentIndexValue);
                    // 先乘内部数组的大小
                    if (currentIndexArraySize > 1) {
                        var currentArraySizeValue = IceConstantData.create(currentIndexArraySize);
                        selector.select(currentArraySizeValue);
                        var sizeReg = selector.emit(currentArraySizeValue);
                        selector.addEmittedInstruction(new ARM64Instruction("MUL {index}, {index}, {size}", indexReg, indexReg, sizeReg));
                    }
                    // 单个大小 等于乘 1
                    // 累加到偏移寄存器
                    if (!indexReg.getType().equals(IceType.I64)) {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}, uxtw", dstReg, dstReg, indexReg));
                    } else {
                        // 如果是64位寄存器
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}", dstReg, dstReg, indexReg));
                    }
                }


                currentIndexArraySize = // 当前一个元素代表的的大小
                        ((IceArrayType) currentType).getElementType() instanceof IceArrayType innerArrayType ? innerArrayType.getTotalSize() : 1;
                if (((IceArrayType) currentType).getElementType() instanceof IceArrayType) {
                    currentType = ((IceArrayType) currentType).getElementType();
                }
            }

            if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                // 如果之前有累加的偏移量
                if (isImm12(accumulatedOffset)) {
                    selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {imm12:offset}", dstReg, dstReg, accumulatedOffset));
                } else {
                    selector.select(accumulatedOffset);
                    var offsetReg = selector.emit(accumulatedOffset);
                    if (!offsetReg.getType().equals(IceType.I64)) {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}, uxtw", dstReg, dstReg, offsetReg));
                    } else {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, dstReg, offsetReg));
                    }
                }

                accumulatedOffset = IceConstantData.create(0); // 清空累加
            }

            assert accumulatedOffset.equals(IceConstantData.create(0));

            // 元素个数乘以元素的bytesize
            if (Tool.isPowerOfTwo(insideType.getByteSize())) {
                var lsl = Tool.log2(insideType.getByteSize());
                selector.addEmittedInstruction(new ARM64Instruction("LSL {dst}, {base}, {imm16:size}", dstReg, dstReg, IceConstantData.create(lsl)));
            } else {
                var insideTypeSize = IceConstantData.create(insideType.getByteSize());
                selector.select(insideTypeSize);
                var imm = selector.emit(insideTypeSize);
                selector.addEmittedInstruction(new ARM64Instruction("MUL {dst}, {base}, {imm}", dstReg, dstReg, imm));
            }


            // 获取全局变量
            var slot = (IceStackSlot) selector.emit(gep.getBasePtr());
            var basePtrReg = selector.getMachineFunction().allocateVirtualRegister(IceType.I64);
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, sp, {local-offset:slot}", basePtrReg, slot));
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, basePtrReg, dstReg));
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (!(value instanceof IceGEPInstruction gep)) {
                return false;
            }

            if (!canBeStackSlot(selector, gep.getBasePtr())) return false;

            for (var index : gep.getIndices()) {
                if (!(index instanceof IceConstantInt) && !canBeReg(selector, index)) {
                    return false;
                }
            }
            return true;
        }
    }

    public static class GEPLoadArgumentPointer extends InstructionPattern<IceGEPInstruction> {
        public GEPLoadArgumentPointer() {
            super(10);
        }

        @Override
        public IceMachineRegister.RegisterView emit(InstructionSelector selector, IceGEPInstruction gep) {
            var dstReg = selector.getMachineFunction().allocateVirtualRegister(gep.getType());
            // 计算偏移量 先计算相对基地址的*元素*个数，最后乘以元素大小
            IceConstantData accumulatedOffset = IceConstantData.create(0); // 累计起来的偏移量 如果出现动态的下表就清空累加到里面

            selector.addEmittedInstruction(new ARM64Instruction("MOV {dst}, #0", dstReg)); // 清空偏移寄存器

            IceType currentType = ((IcePtrType<?>) gep.getBasePtr().getType()).getPointTo();
            // 当前一个元素代表的的大小
            IceType insideType;
            int currentIndexArraySize;
            if (currentType instanceof IceArrayType) {
                IceArrayType currentArrayType = (IceArrayType) currentType;
                currentIndexArraySize = currentArrayType.getTotalSize();
                insideType = ((IceArrayType) currentType).getInsideElementType(); // 最内的元素类型
            } else {
                currentIndexArraySize = 1; // 如果不是数组类型，默认大小为1
                insideType = currentType; // 最内的元素类型就是当前类型
            }


            assert !insideType.isArray() && !insideType.isPointer();
            for (var i = 0; i < gep.getIndices().size(); i++) {
                var currentIndexValue = gep.getIndices().get(i);
                if (currentIndexValue instanceof IceConstantInt constIndex) {
                    // 这个下标是编译时常量
                    var offset = constIndex.multiply(IceConstantData.create(currentIndexArraySize));
                    accumulatedOffset = accumulatedOffset.plus(offset);
                } else {
                    // 这个下标是动态的
                    if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                        // 如果之前有累加的偏移量
                        if (isImm12(accumulatedOffset)) {
                            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {imm12:offset}", dstReg, dstReg, accumulatedOffset));
                        } else {
                            selector.select(accumulatedOffset);
                            var offsetReg = selector.emit(accumulatedOffset);
                            if (!offsetReg.getType().equals(IceType.I64)) {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}, uxtw", dstReg, dstReg, offsetReg));
                            } else {
                                selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, dstReg, offsetReg));
                            }
                        }

                        accumulatedOffset = IceConstantData.create(0); // 清空累加
                    }
                    // 生成加入动态下标的指令
                    var indexReg = selector.emit(currentIndexValue);
                    // 先乘内部数组的大小
                    if (currentIndexArraySize > 1) {
                        var currentArraySizeValue = IceConstantData.create(currentIndexArraySize);
                        selector.select(currentArraySizeValue);
                        var sizeReg = selector.emit(currentArraySizeValue);
                        selector.addEmittedInstruction(new ARM64Instruction("MUL {index}, {index}, {size}", indexReg, indexReg, sizeReg));
                    }
                    // 单个大小 等于乘 1
                    // 累加到偏移寄存器
                    if (!indexReg.getType().equals(IceType.I64)) {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}, uxtw", dstReg, dstReg, indexReg));
                    } else {
                        // 如果是64位寄存器
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {index}", dstReg, dstReg, indexReg));
                    }
                }


                if (currentType instanceof IceArrayType arrayType) {
                    currentIndexArraySize = // 当前一个元素代表的的大小
                            arrayType.getElementType() instanceof IceArrayType innerArrayType ? innerArrayType.getTotalSize() : 1;
                    if (arrayType.getElementType() instanceof IceArrayType) {
                        currentType = arrayType.getElementType();
                    }
                }
            }

            if (!accumulatedOffset.equals(IceConstantData.create(0))) {
                // 如果之前有累加的偏移量
                if (isImm12(accumulatedOffset)) {
                    selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {imm12:offset}", dstReg, dstReg, accumulatedOffset));
                } else {
                    selector.select(accumulatedOffset);
                    var offsetReg = selector.emit(accumulatedOffset);
                    if (!offsetReg.getType().equals(IceType.I64)) {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}, uxtw", dstReg, dstReg, offsetReg));
                    } else {
                        selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, dstReg, offsetReg));
                    }
                }

                accumulatedOffset = IceConstantData.create(0); // 清空累加
            }

            assert accumulatedOffset.equals(IceConstantData.create(0));

            // 元素个数乘以元素的bytesize
            if (Tool.isPowerOfTwo(insideType.getByteSize())) {
                var lsl = Tool.log2(insideType.getByteSize());
                selector.addEmittedInstruction(new ARM64Instruction("LSL {dst}, {base}, {imm:size}", dstReg, dstReg, IceConstantData.create(lsl)));
            } else {
                var insideTypeSize = IceConstantData.create(insideType.getByteSize());
                selector.select(insideTypeSize);
                var imm = selector.emit(insideTypeSize);
                selector.addEmittedInstruction(new ARM64Instruction("MUL {dst}, {base}, {imm}", dstReg, dstReg, imm));
            }


            // 获取函数参数的内容（为指针）
            var basePtrReg = (IceMachineRegister.RegisterView) selector.emit(gep.getBasePtr());

            // 如果有累加的偏移量
            selector.addEmittedInstruction(new ARM64Instruction("ADD {dst}, {base}, {offset}", dstReg, basePtrReg, dstReg));
            return dstReg;
        }

        @Override
        public boolean test(InstructionSelector selector, IceValue value) {
            if (!(value instanceof IceGEPInstruction gep)) {
                return false;
            }

            if (!(gep.getBasePtr() instanceof IceFunction.IceFunctionParameter)) return false;

            for (var index : gep.getIndices()) {
                if (!(index instanceof IceConstantInt) && !canBeReg(selector, index)) {
                    return false;
                }
            }
            return true;
        }
    }

}