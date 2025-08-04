package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 负责生成函数序言和尾声指令序列。
 * 指令生成的决策树：
 * <pre>
 * isHasCall
 * ├── false: LeafFunction (只需要分配栈空间)
 * │   ├── stackSize == 0: 不生成指令
 * │   └── stackSize > 0: SUB sp + MOV x29
 * │
 * └── true: CallFunction (需要保存返回地址)
 *     ├── argumentSize == 0: NoArgsCallFunction
 *     │   ├── 小栈帧: STP(!) + MOV
 *     │   └── 大栈帧: SUB + STP + 恢复
 *     │
 *     └── argumentSize > 0: WithArgsCallFunction
 *         ├── 小栈帧: SUB + STP + ADD
 *         └── 大栈帧: 循环SUB + STP + ADD
 * </pre>
 */
public class PrologueEpilogueGenerator {

    private static boolean isSTPImmediate(int offset) {
        return offset >= -512 && offset <= 504;
    }

    private static boolean isArithmeticImmediate(int offset) {
        return offset >= 0 && offset <= 4096;
    }

    /**
     * 指令生成器的基类，提供共用的栈操作方法
     */
    private abstract static class InstructionGenerator {
        /**
         * 生成调整栈指针的指令序列
         * @param size 调整的大小
         * @param isSub 是否是减法操作
         */
        protected List<IceMachineInstruction> generateStackAdjustment(int size, boolean isSub) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            String op = isSub ? "SUB" : "ADD";
            final int operationMaxSize = 0xFFFFFF; // 两条SUB或者 ADD指令的最大立即数范围

            int remainingSize = size;
            while (remainingSize > 0) {
                int operationSize = Math.min(remainingSize, operationMaxSize);
                int highBit = (operationSize >> 12) & 0xFFF;
                int lowBit = operationSize & 0xFFF;
                if (lowBit != 0) {
                    instructions.add(new ARM64Instruction(op + " sp, sp, {imm:stack}", IceConstantData.create(lowBit)));
                }
                if (highBit != 0) {
                    instructions.add(new ARM64Instruction(op + " sp, sp, {imm:stack}, lsl 12", IceConstantData.create(highBit)));
                }
                remainingSize -= operationSize;
            }
            return instructions;
        }

        /**
         * 生成保存调用者寄存器的指令序列 确保sp位于上一个栈帧参数结束位置，即还未移动
         */
        protected List<IceMachineInstruction> generateCalleeSaveCode(List<IceStackSlot.SavedRegisterStackSlot> calleeSavedSlots) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            for (int i = 0; i < calleeSavedSlots.size(); ) {
                var currentSlot = calleeSavedSlots.get(i);
                var currentReg = currentSlot.getRegister();

                // 尝试与下一个寄存器配对
                if (i + 1 < calleeSavedSlots.size()) {
                    var nextSlot = calleeSavedSlots.get(i + 1);
                    var nextReg = nextSlot.getRegister();
                    // 仅当类型相同时才能配对
                    if (currentReg.getType().equals(nextReg.getType())) {
                        int size = currentSlot.getType().getByteSize() * 2;
                        instructions.add(new ARM64Instruction("STP {reg1}, {reg2}, [sp, {imm:stack}]!",
                                currentReg.createView(currentReg.getType()), nextReg.createView(nextReg.getType()), IceConstantData.create(-size)));
                        i += 2;
                        continue;
                    }
                }

                // 处理单个寄存器，始终分配16字节以保证对齐
                instructions.add(new ARM64Instruction("STR {reg1}, [sp, {imm:stack}]!",
                        currentReg.createView(currentReg.getType()), IceConstantData.create(-16)));
                i++;
            }
            return instructions;
        }

        /**
         * 生成加载被调用者寄存器的指令序列 确保sp位于上一个CalleeSave区结束的位置
         */
        protected List<IceMachineInstruction> generateCalleeLoadCode(List<IceStackSlot.SavedRegisterStackSlot> calleeSavedSlots) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            for (int i = 0; i < calleeSavedSlots.size(); ) {
                var currentSlot = calleeSavedSlots.get(i);
                var currentReg = currentSlot.getRegister();

                // 尝试与下一个寄存器配对
                if (i + 1 < calleeSavedSlots.size()) {
                    var nextSlot = calleeSavedSlots.get(i + 1);
                    var nextReg = nextSlot.getRegister();
                    // 仅当类型相同时才能配对
                    if (currentReg.getType().equals(nextReg.getType())) {
                        int size = currentSlot.getType().getByteSize() * 2;
                        instructions.add(new ARM64Instruction("LDP {reg1}, {reg2}, [sp], {imm:stack}",
                                currentReg.createView(currentReg.getType()), nextReg.createView(nextReg.getType()), IceConstantData.create(size)));

                        i += 2;
                        continue;
                    }
                }

                // 处理单个寄存器，始终分配16字节以保证对齐
                instructions.add(new ARM64Instruction("LDR {reg1}, [sp], {imm:stack}",
                        currentReg.createView(currentReg.getType()), IceConstantData.create(16)));
                i++;
            }
            Collections.reverse(instructions); // 利用栈的FILO特性加载
            return instructions;
        }
        /**
         * 生成函数序言指令序列
         */
        public abstract List<IceMachineInstruction> generatePrologue(AlignFramePass.AlignedStackFrame frame);

        /**
         * 生成函数尾声指令序列
         */
        public abstract List<IceMachineInstruction> generateEpilogue(AlignFramePass.AlignedStackFrame frame);
    }

    /**
     * 叶子函数的指令生成器
     * - 只需要分配栈空间
     * - 不需要保存返回地址
     */
    private static class LeafFunctionGenerator extends InstructionGenerator {
        @Override
        public List<IceMachineInstruction> generatePrologue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>(generateCalleeSaveCode(frame.getCalleeSavedSlots()));
            int stackToAllocate = frame.getStackSize() - frame.getCalleeSavedSize();
            if (stackToAllocate > 0) {
                if (isArithmeticImmediate(stackToAllocate)) {
                    instructions.add(new ARM64Instruction("SUB sp, sp, {imm:stack}",
                            IceConstantData.create(stackToAllocate)));
                    instructions.add(new ARM64Instruction("MOV x29, sp"));
                } else {
                    instructions.addAll(generateStackAdjustment(stackToAllocate, true));
                    instructions.add(new ARM64Instruction("MOV x29, sp"));
                }
            }
            return instructions;
        }

        @Override
        public List<IceMachineInstruction> generateEpilogue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            int stackToDeallocate = frame.getStackSize() - frame.getCalleeSavedSize();
            if (stackToDeallocate > 0) {
                if (isArithmeticImmediate(stackToDeallocate)) {
                    instructions.add(new ARM64Instruction("ADD sp, sp, {imm:stack}",
                            IceConstantData.create(stackToDeallocate)));
                } else {
                    instructions.addAll(generateStackAdjustment(stackToDeallocate, false));
                }
            }
            instructions.addAll(generateCalleeLoadCode(frame.getCalleeSavedSlots()));
            return instructions;
        }
    }

    /**
     * 无参数函数调用的指令生成器
     * - 需要保存返回地址
     * - 无需处理参数区
     */
    private static class NoArgsCallFunctionGenerator extends InstructionGenerator {
        @Override
        public List<IceMachineInstruction> generatePrologue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>(generateCalleeSaveCode(frame.getCalleeSavedSlots()));
            int stackToAllocate = frame.getStackSize() - frame.getCalleeSavedSize();
            // 有函数调用但没有栈上参数
            if (isSTPImmediate(-stackToAllocate)) {
                // 小栈帧：使用STP的立即数寻址
                instructions.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]!",
                        IceConstantData.create(-stackToAllocate)));
                instructions.add(new ARM64Instruction("MOV x29, sp"));
            } else {
                // 大栈帧：需要分开处理
                if (isArithmeticImmediate(stackToAllocate)) {
                    instructions.add(new ARM64Instruction("SUB sp, sp, {imm:stack}",
                            IceConstantData.create(stackToAllocate)));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp]"));
                } else {
                    instructions.addAll(generateStackAdjustment(stackToAllocate, true));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp]"));
                }
            }
            return instructions;
        }

        @Override
        public List<IceMachineInstruction> generateEpilogue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            int stackToDeallocate = frame.getStackSize() - frame.getCalleeSavedSize();
            // 有函数调用但没有栈上参数
            if (isSTPImmediate(stackToDeallocate)) {
                // 小栈帧：使用STP的立即数寻址
                instructions.add(new ARM64Instruction("LDP x29, x30, [sp], {imm:stack}",
                        IceConstantData.create(stackToDeallocate)));
            } else {
                // 大栈帧：需要分开处理
                instructions.add(new ARM64Instruction("LDP x29, x30, [sp]"));
                if (isArithmeticImmediate(stackToDeallocate)) {
                    instructions.add(new ARM64Instruction("ADD sp, sp, {imm:stack}",
                            IceConstantData.create(stackToDeallocate)));
                } else {
                    instructions.addAll(generateStackAdjustment(stackToDeallocate, false));
                }
            }
            instructions.addAll(generateCalleeLoadCode(frame.getCalleeSavedSlots()));
            return instructions;
        }
    }

    /**
     * 有参数函数调用的指令生成器
     * - 需要保存返回地址
     * - 需要处理参数区
     */
    private static class WithArgsCallFunctionGenerator extends InstructionGenerator {
        @Override
        public List<IceMachineInstruction> generatePrologue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>(generateCalleeSaveCode(frame.getCalleeSavedSlots()));
            var argSize = frame.getArgumentSize();
            var stackToAllocate = frame.getStackSize() - frame.getCalleeSavedSize();
            var remainSize = stackToAllocate - argSize;

            if (isSTPImmediate(argSize)) {
                // 参数区在STP范围内：保持原有逻辑
                if (isArithmeticImmediate(stackToAllocate)) {
                    // 小栈帧：直接使用立即数
                    instructions.add(new ARM64Instruction("SUB sp, sp, {imm:stack}",
                            IceConstantData.create(stackToAllocate)));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]",
                            IceConstantData.create(argSize)));
                    instructions.add(new ARM64Instruction("ADD x29, sp, {imm:stack}",
                            IceConstantData.create(argSize)));
                } else {
                    // 大栈帧：使用循环调整
                    instructions.addAll(generateStackAdjustment(stackToAllocate, true));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]",
                            IceConstantData.create(argSize)));
                    instructions.add(new ARM64Instruction("ADD x29, sp, {imm:stack}",
                            IceConstantData.create(argSize)));
                }
            } else {
                // 参数区超出范围时的处理
                // 1. 分配变量区和返回地址区
                instructions.addAll(generateStackAdjustment(remainSize, true));
                // 2. 保存帧指针和返回地址
                instructions.add(new ARM64Instruction("STP x29, x30, [sp]"));
                instructions.add(new ARM64Instruction("MOV x29, sp"));
                // 3. 分配参数区
                instructions.addAll(generateStackAdjustment(argSize, true));
            }
            return instructions;
        }

        @Override
        public List<IceMachineInstruction> generateEpilogue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            var argSize = frame.getArgumentSize();
            var stackToDeallocate = frame.getStackSize() - frame.getCalleeSavedSize();
            var remainSize = stackToDeallocate - argSize;

            if (isSTPImmediate(argSize)) {
                // 参数区在STP范围内：保持原有逻辑
                instructions.add(new ARM64Instruction("LDP x29, x30, [sp, {imm:stack}]",
                        IceConstantData.create(argSize)));
                if (isArithmeticImmediate(stackToDeallocate)) {
                    // 小栈帧：直接使用立即数
                    instructions.add(new ARM64Instruction("ADD sp, sp, {imm:stack}",
                            IceConstantData.create(stackToDeallocate)));
                } else {
                    // 大栈帧：使用循环调整
                    instructions.addAll(generateStackAdjustment(stackToDeallocate, false));
                }
            } else {
                // 参数区超出范围时的处理
                // 1. 回收参数区
                instructions.addAll(generateStackAdjustment(argSize, false));
                // 2. 恢复帧指针和返回地址
                instructions.add(new ARM64Instruction("LDP x29, x30, [sp]"));
                // 3. 回收变量区和返回地址区
                instructions.addAll(generateStackAdjustment(remainSize, false));
            }
            instructions.addAll(generateCalleeLoadCode(frame.getCalleeSavedSlots()));
            return instructions;
        }
    }

    /**
     * 根据函数特征选择合适的指令生成器
     */
    private InstructionGenerator selectGenerator(AlignFramePass.AlignedStackFrame frame) {
        // 叶子函数
        if (!frame.isHasCall()) {
            return new LeafFunctionGenerator();
        }
        
        // 有调用的函数
        if (frame.getArgumentSize() == 0) {
            // 无参数调用
            return new NoArgsCallFunctionGenerator();
        } else {
            // 有参数调用
            return new WithArgsCallFunctionGenerator();
        }
    }

    /**
     * 生成函数序言指令序列
     */
    public List<IceMachineInstruction> generatePrologue(AlignFramePass.AlignedStackFrame frame) {
        return selectGenerator(frame).generatePrologue(frame);
    }

    /**
     * 生成函数尾声指令序列
     */
    public List<IceMachineInstruction> generateEpilogue(AlignFramePass.AlignedStackFrame frame) {
        return selectGenerator(frame).generateEpilogue(frame);
    }
}
