package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IceType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceMachineRegister;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Tool;
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
@Pass(group = {"O0", "backend"})
public class AlignFramePass implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {

    public static boolean isSTPImmediate(int offset) {
        return Tool.inRange(offset, -512, 504);
    }

    private static final int arithmeticImmediateUpperBound = 4096; // ARM64 的算术立即数上限
    public static boolean isArithmeticImmediate(int offset) {
        return Tool.inRange(offset, 0, 4096);
    }

    private static class AlignedStackFrame {
        public IceMachineFunction function;
        public List<IceMachineInstruction> prologueList = new ArrayList<>();
        public List<IceMachineInstruction> epilogueList = new ArrayList<>();

        public List<IceStackSlot.VariableStackSlot> variableSlots = new ArrayList<>(); // 所有变量栈槽
        public List<IceStackSlot.ArgumentStackSlot> argumentSlots = new ArrayList<>(); // 所有调用参数栈槽
        public List<IceStackSlot.ParameterStackSlot> parameterSlots = new ArrayList<>(); // 所有函数参数栈槽

        public int stackSize = 0;
        public int returnRegisterSize = 0; // 保存返回地址的寄存器大小
        public int argumentSize = 0; // 参数区大小
        public final int dynamicStackSize = 0; // 动态栈大小，暂时不使用
        public int variableSize = 0; // 变量区大小

        public AlignedStackFrame(IceMachineFunction function) {
            this.function = function;


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

        /**
         * 计算栈帧中所有变量的大小
         */
        public void calcVariableSize() {
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
        public void calcArgumentSize() {
            // 计算调用参数区大小

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
        var currentStackFrame = new AlignedStackFrame(target);

        // Phase 1: 计算栈大小，生成函数序言和尾声

        currentStackFrame.calcVariableSize();
        currentStackFrame.calcArgumentSize();

        currentStackFrame.stackSize += currentStackFrame.variableSize + currentStackFrame.argumentSize; // 栈帧大小 = 变量区大小 + 参数区大小 + 返回寄存器大小

        // TODO 这个 while 开栈是在太慢了，换成SUB + lsl标志
        if (target.isHasCall()) { // 如果函数有调用外部函数，则需要保存返回地址
            currentStackFrame.returnRegisterSize = 2 * 8; // 大小为16字节依旧对齐/.
            currentStackFrame.stackSize += currentStackFrame.returnRegisterSize;
            if (currentStackFrame.argumentSize != 0) {
                // 有函数调用且有栈上参数 => 保存返回地址和帧指针
                if (isArithmeticImmediate(currentStackFrame.stackSize)) {
                    currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(currentStackFrame.stackSize)));
                    currentStackFrame.prologueList.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]", IceConstantData.create(currentStackFrame.argumentSize)));
                    currentStackFrame.prologueList.add(new ARM64Instruction("ADD x29, sp, {imm:stack}", IceConstantData.create(currentStackFrame.argumentSize)));

                    currentStackFrame.epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp, {imm:stack}]", IceConstantData.create(currentStackFrame.argumentSize)));
                    currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(currentStackFrame.stackSize)));
                } else {
                    // 处理大栈帧,循环SUB/ADD
                    int remainingSize = currentStackFrame.stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                    currentStackFrame.prologueList.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]", IceConstantData.create(currentStackFrame.argumentSize)));
                    currentStackFrame.prologueList.add(new ARM64Instruction("ADD x29, sp, {imm:stack}", IceConstantData.create(currentStackFrame.argumentSize)));

                    currentStackFrame.epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp, {imm:stack}]", IceConstantData.create(currentStackFrame.argumentSize)));
                    // 处理栈帧恢复
                    remainingSize = currentStackFrame.stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                }
            } else {
                // 有函数调用但没有栈上参数 => 只需要保存返回地址和帧指针
                if (isSTPImmediate(currentStackFrame.stackSize)) {
                    // 处理小的栈帧
                    currentStackFrame.prologueList.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]!", IceConstantData.create(-currentStackFrame.stackSize)));
                    currentStackFrame.prologueList.add(new ARM64Instruction("MOV x29, sp"));

                    currentStackFrame.epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp], {imm:stack}", IceConstantData.create(currentStackFrame.stackSize)));
                } else {
                    // 处理大的栈帧
                    if (isArithmeticImmediate(currentStackFrame.stackSize)) {
                        currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(currentStackFrame.stackSize)));
                        currentStackFrame.prologueList.add(new ARM64Instruction("STP x29, x30, [sp]"));

                        currentStackFrame.epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp]"));
                        currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(currentStackFrame.stackSize)));
                    } else {
                        // 处理大栈帧,循环SUB/ADD
                        int remainingSize = currentStackFrame.stackSize;
                        while (remainingSize > arithmeticImmediateUpperBound) {
                            currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                            remainingSize -= arithmeticImmediateUpperBound;
                        }
                        if (remainingSize > 0) {
                            currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                        }
                        currentStackFrame.prologueList.add(new ARM64Instruction("STP x29, x30, [sp]"));

                        currentStackFrame.epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp]"));
                        // 处理栈帧恢复
                        remainingSize = currentStackFrame.stackSize;
                        while (remainingSize > arithmeticImmediateUpperBound) {
                            currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                            remainingSize -= arithmeticImmediateUpperBound;
                        }
                        if (remainingSize > 0) {
                            currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                        }
                    }
                }

            }
        } else {
            // Leaf function => 只用分配栈空间
            if (currentStackFrame.stackSize > 0) {
                if (isArithmeticImmediate(currentStackFrame.stackSize)) {
                    currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(currentStackFrame.stackSize)));
                    currentStackFrame.prologueList.add(new ARM64Instruction("MOV x29, sp"));

                    currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(currentStackFrame.stackSize)));
                } else {
                    // 处理大栈帧,循环SUB/ADD
                    int remainingSize = currentStackFrame.stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        currentStackFrame.prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                    currentStackFrame.prologueList.add(new ARM64Instruction("MOV x29, sp"));

                    // 处理栈帧恢复
                    remainingSize = currentStackFrame.stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        currentStackFrame.epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                }
            }
        }

        currentStackFrame.stackSize += currentStackFrame.returnRegisterSize; // 栈帧大小 = 变量区大小 + 参数区大小 + 返回寄存器大小

        // Phase 2: entry 插入函数序言
        currentStackFrame.prologueList.forEach(instr -> instr.setParent(target.getEntryBlock()));
        target.getEntryBlock().addAll(0, currentStackFrame.prologueList);

        // Phase 3: 在每个块的末尾插入尾声
        for (var block : target) {
            if (block.successors().isEmpty()) {
                var inserted = false;
                for (var i = block.size() - 1; i >= 0; i--) {
                    if (!block.get(i).isTerminal()) {
                        var clonedInstructions = currentStackFrame.epilogueList.stream().map(instr -> {
                            var cloned = instr.clone();
                            cloned.setParent(block);
                            return cloned;
                        }).toList();
                        block.addAll(i + 1, clonedInstructions);
                        inserted = true;
                        break;
                    }
                }
                if (!inserted) {
                    var clonedInstructions = currentStackFrame.epilogueList.stream().map(instr -> {
                        var cloned = instr.clone();
                        cloned.setParent(block);
                        return cloned;
                    }).toList();
                    block.addAll(0, clonedInstructions);
                }
            }
        }

        // Phase 4: 设置栈槽偏移量
        // 设置参数栈槽偏移量
        currentStackFrame.doSetParameterOffset();
        currentStackFrame.doSetArgumentOffset();
        currentStackFrame.doSetVariableOffset();

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
