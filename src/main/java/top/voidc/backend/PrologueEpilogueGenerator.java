package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.machine.IceMachineInstruction;

import java.util.ArrayList;
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
    private static final int ARITHMETIC_IMMEDIATE_UPPER_BOUND = 4096;

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
            
            int remainingSize = size;
            while (remainingSize > ARITHMETIC_IMMEDIATE_UPPER_BOUND) {
                instructions.add(new ARM64Instruction(op + " sp, sp, {imm:stack}", 
                        IceConstantData.create(ARITHMETIC_IMMEDIATE_UPPER_BOUND)));
                remainingSize -= ARITHMETIC_IMMEDIATE_UPPER_BOUND;
            }
            if (remainingSize > 0) {
                instructions.add(new ARM64Instruction(op + " sp, sp, {imm:stack}", 
                        IceConstantData.create(remainingSize)));
            }
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
            List<IceMachineInstruction> instructions = new ArrayList<>();
            if (frame.getStackSize() > 0) {
                if (isArithmeticImmediate(frame.getStackSize())) {
                    instructions.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", 
                            IceConstantData.create(frame.getStackSize())));
                    instructions.add(new ARM64Instruction("MOV x29, sp"));
                } else {
                    instructions.addAll(generateStackAdjustment(frame.getStackSize(), true));
                    instructions.add(new ARM64Instruction("MOV x29, sp"));
                }
            }
            return instructions;
        }

        @Override
        public List<IceMachineInstruction> generateEpilogue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            if (frame.getStackSize() > 0) {
                if (isArithmeticImmediate(frame.getStackSize())) {
                    instructions.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", 
                            IceConstantData.create(frame.getStackSize())));
                } else {
                    instructions.addAll(generateStackAdjustment(frame.getStackSize(), false));
                }
            }
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
            List<IceMachineInstruction> instructions = new ArrayList<>();
            // 有函数调用但没有栈上参数
            if (isSTPImmediate(-frame.getStackSize())) {
                // 小栈帧：使用STP的立即数寻址
                instructions.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]!", 
                        IceConstantData.create(-frame.getStackSize())));
                instructions.add(new ARM64Instruction("MOV x29, sp"));
            } else {
                // 大栈帧：需要分开处理
                if (isArithmeticImmediate(frame.getStackSize())) {
                    instructions.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", 
                            IceConstantData.create(frame.getStackSize())));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp]"));
                } else {
                    instructions.addAll(generateStackAdjustment(frame.getStackSize(), true));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp]"));
                }
            }
            return instructions;
        }

        @Override
        public List<IceMachineInstruction> generateEpilogue(AlignFramePass.AlignedStackFrame frame) {
            List<IceMachineInstruction> instructions = new ArrayList<>();
            // 有函数调用但没有栈上参数
            if (isSTPImmediate(frame.getStackSize())) {
                // 小栈帧：使用STP的立即数寻址
                instructions.add(new ARM64Instruction("LDP x29, x30, [sp], {imm:stack}", 
                        IceConstantData.create(frame.getStackSize())));
            } else {
                // 大栈帧：需要分开处理
                instructions.add(new ARM64Instruction("LDP x29, x30, [sp]"));
                if (isArithmeticImmediate(frame.getStackSize())) {
                    instructions.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", 
                            IceConstantData.create(frame.getStackSize())));
                } else {
                    instructions.addAll(generateStackAdjustment(frame.getStackSize(), false));
                }
            }
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
            List<IceMachineInstruction> instructions = new ArrayList<>();
            var argSize = frame.getArgumentSize();
            var remainSize = frame.getStackSize() - argSize;

            if (isSTPImmediate(frame.getArgumentSize())) {
                // 参数区在STP范围内：保持原有逻辑
                if (isArithmeticImmediate(frame.getStackSize())) {
                    // 小栈帧：直接使用立即数
                    instructions.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", 
                            IceConstantData.create(frame.getStackSize())));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]", 
                            IceConstantData.create(frame.getArgumentSize())));
                    instructions.add(new ARM64Instruction("ADD x29, sp, {imm:stack}", 
                            IceConstantData.create(frame.getArgumentSize())));
                } else {
                    // 大栈帧：使用循环调整
                    instructions.addAll(generateStackAdjustment(frame.getStackSize(), true));
                    instructions.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]", 
                            IceConstantData.create(frame.getArgumentSize())));
                    instructions.add(new ARM64Instruction("ADD x29, sp, {imm:stack}", 
                            IceConstantData.create(frame.getArgumentSize())));
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
            var remainSize = frame.getStackSize() - argSize;

            if (isSTPImmediate(frame.getArgumentSize())) {
                // 参数区在STP范围内：保持原有逻辑
                instructions.add(new ARM64Instruction("LDP x29, x30, [sp, {imm:stack}]", 
                        IceConstantData.create(frame.getArgumentSize())));
                if (isArithmeticImmediate(frame.getStackSize())) {
                    // 小栈帧：直接使用立即数
                    instructions.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", 
                            IceConstantData.create(frame.getStackSize())));
                } else {
                    // 大栈帧：使用循环调整
                    instructions.addAll(generateStackAdjustment(frame.getStackSize(), false));
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
