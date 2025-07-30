package top.voidc.backend;

import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 对齐栈帧并设置偏移量，同时插入函数序言和尾声还有 插入保存寄存器
 * <br>
 * <a href="https://blog.csdn.net/anyegongjuezjd/article/details/107173140">AArch 64 栈帧</a>
 */
@Pass(group = {"O0", "backend"}, parallel = true)
public class AlignFramePass implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    public static class AlignedStackFrame {
        private final IceMachineFunction function;
        private final List<IceStackSlot.VariableStackSlot> variableSlots = new ArrayList<>(); // 所有变量栈槽
        private final List<IceStackSlot.ArgumentStackSlot> argumentSlots = new ArrayList<>(); // 所有调用参数栈槽
        private final List<IceStackSlot.ParameterStackSlot> parameterSlots = new ArrayList<>(); // 所有函数参数栈槽

        private int stackSize = 0;
        private int returnRegisterSize = 0; // 保存返回地址的寄存器大小
        private int argumentSize = 0; // 参数区大小
        private final int dynamicStackSize = 0; // 动态栈大小，暂时不使用
        private int variableSize = 0; // 变量区大小
        private final boolean hasCall; // 是否有函数调用

        public int getStackSize() { return stackSize; }
        public void addStackSize(int size) { this.stackSize += size; }
        public void setReturnRegisterSize(int size) { this.returnRegisterSize = size; }
        public int getReturnRegisterSize() { return returnRegisterSize; }
        public int getArgumentSize() { return argumentSize; }
        public int getVariableSize() { return variableSize; }
        public int getDynamicStackSize() { return dynamicStackSize; }
        public boolean isHasCall() { return hasCall; }

        public AlignedStackFrame(IceMachineFunction function) {
            this.function = function;
            this.hasCall = function.isHasCall();

            for (var slot : function.getStackFrame()) {
                switch (slot) {
                    case IceStackSlot.ArgumentStackSlot argSlot -> argumentSlots.add(argSlot);
                    case IceStackSlot.VariableStackSlot varSlot -> variableSlots.add(varSlot);
                    case IceStackSlot.ParameterStackSlot paramSlot -> parameterSlots.add(paramSlot); // 保存寄存器栈槽不需要计算大小
                    default -> {
                        // 其他类型不处理
                    }
                }
            }
        }

        public void calculateSizes() {
            calcVariableSize();
            calcArgumentSize();

            stackSize = variableSize + argumentSize + dynamicStackSize;

            if (hasCall) {
                returnRegisterSize = 2 * 8; // 大小为16字节,保存x29和x30
                stackSize += returnRegisterSize;
            }
        }

        /**
         * 计算栈帧中所有变量的大小
         */
        private void calcVariableSize() {
            // 收集所有变量栈槽并按大小排序
            variableSlots.sort(Comparator.comparingInt(a -> a.getType().getByteSize()));
            for (var varSlot : variableSlots) {
                if (variableSize % varSlot.getAlignment() != 0) {
                    // 对齐到当前栈槽的对齐要求
                    variableSize += varSlot.getAlignment() - (variableSize % varSlot.getAlignment());
                }
                variableSize += varSlot.getType().getByteSize();
            }

            if (variableSize % 16 != 0) {
                // 对齐到 16 字节
                variableSize += 16 - (variableSize % 16);
            }
        }

        /**
         * 计算调用参数区大小
         */
        private void calcArgumentSize() {
            var callArguments = argumentSlots.stream().collect(Collectors.groupingBy(IceStackSlot.ArgumentStackSlot::getCallInstruction));

            for (var args : callArguments.values()) {
                // 计算当前调用指令的参数区大小
                int currentArgumentSize = 0;
                for (var argSlot : args) {
                    currentArgumentSize += Math.max(8, argSlot.getType().getByteSize()); // 确保每个参数至少占用 8 字节
                }

                // 取最大值作为当前调用指令的参数区大小
                argumentSize = Math.max(argumentSize, currentArgumentSize);
            }

            if (argumentSize % 16 != 0) {
                // 对齐到 16 字节
                argumentSize += 16 - (argumentSize % 16);
            }
        }

        public void doSetArgumentOffset() {
            var callArguments = argumentSlots.stream().collect(Collectors.groupingBy(IceStackSlot.ArgumentStackSlot::getCallInstruction));

            for (var args : callArguments.values()) {
                var sortedArgs = args.stream()
                        .sorted(Comparator.comparingInt(IceStackSlot.ArgumentStackSlot::getArgumentIndex))
                        .toList();
                int currentOffset = 0; // 参数从 sp 指针开始向高地址分配
                for (var argSlot : sortedArgs) {
                    argSlot.setOffset(currentOffset);
                    currentOffset += Math.max(8, argSlot.getType().getByteSize());
                }
            }
        }

        /**
         * 设置参数栈槽偏移量
         * 调用时确保整个栈帧的大小已经计算完成
         */
        public void doSetParameterOffset() {
            // 设置参数栈槽偏移量 跳过整个栈帧
            var sortedParameterSlots = parameterSlots.stream()
                    .sorted(Comparator.comparingInt(IceStackSlot.ParameterStackSlot::getParameterIndex))
                    .toList();
            int currentOffset = 0; // 参数从 sp 指针开始向高地址分配
            for (var paramSlot : sortedParameterSlots) {
                paramSlot.setOffset(currentOffset + stackSize); // 跳过整个栈帧的大小
                // 计算下一个参数的偏移量
                currentOffset += Math.max(8, paramSlot.getType().getByteSize());
            }
        }

        public void doSetVariableOffset() {
            // 设置变量栈槽偏移量 - 使用已排序的变量列表
            var currentOffset = 0; // 变量从sp指针开始开始向高地址分配
            var variableRegionBase = argumentSize + dynamicStackSize + returnRegisterSize;
            for (var varSlot : variableSlots) {
                // 对齐确保当前地址是对齐到要求的
                if (currentOffset % varSlot.getAlignment() != 0) {
                    currentOffset += varSlot.getAlignment() - (currentOffset % varSlot.getAlignment());
                }
                varSlot.setOffset(currentOffset + variableRegionBase); // 有为内部函数调用准备的栈帧和 lr + fp

                // 计算下一个变量的偏移量
                var currentVariableSize = varSlot.getType().getByteSize();
                currentOffset += currentVariableSize;
            }
        }
    }

    @Override
    public boolean run(IceMachineFunction target) {
        var frame = new AlignedStackFrame(target);
        frame.calculateSizes();
        
        var generator = new PrologueEpilogueGenerator();
        var prologueInstructions = generator.generatePrologue(frame);
        var epilogueInstructions = generator.generateEpilogue(frame);
        
        // 在entry block插入序言指令
        prologueInstructions.forEach(instr -> instr.setParent(target.getEntryBlock()));
        target.getEntryBlock().addAll(0, prologueInstructions);

        // 在每个终止块的末尾插入尾声指令
        for (var block : target) {
            if (block.successors().isEmpty()) {
                // 克隆尾声指令并设置父节点
                var clonedInstructions = epilogueInstructions.stream()
                    .map(instr -> {
                        var cloned = instr.clone();
                        cloned.setParent(block);
                        return cloned;
                    })
                    .toList();

                // 查找合适的插入位置：最后一个非终止指令之后，如果没有则插入到开头
                var insertIndex = 0;
                for (var i = block.size() - 1; i >= 0; i--) {
                    if (!block.get(i).isTerminal()) {
                        insertIndex = i + 1;
                        break;
                    }
                }
                block.addAll(insertIndex, clonedInstructions);
            }
        }

        // 设置栈槽偏移量
        frame.doSetParameterOffset();
        frame.doSetArgumentOffset();
        frame.doSetVariableOffset();

        return true;
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
}
