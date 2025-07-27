package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.Tool;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.Comparator;

/**
 * 对齐栈帧并设置偏移量，同时插入函数序言和尾声
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

    @Override
    public boolean run(IceMachineFunction target) {
        var prologueList = new ArrayList<IceMachineInstruction>();
        var epilogueList = new ArrayList<IceMachineInstruction>();
        var stackSize = 0;

        // Phase 1: 计算栈大小

        // 收集所有变量栈槽并按大小排序
        var variableSlots = new ArrayList<IceStackSlot.VariableStackSlot>();
        for (var slot : target.getStackFrame()) {
            if (slot instanceof IceStackSlot.VariableStackSlot varSlot) {
                variableSlots.add(varSlot);
            }
        }
        variableSlots.sort(Comparator.comparingInt(a -> a.getType().getByteSize()));

        // 计算栈帧中所有变量的大小
        var variableSize = 0;
        for (var varSlot : variableSlots) {
            if (variableSize % varSlot.getAlignment() != 0) {
                // 对齐到当前栈槽的对齐要求
                variableSize += varSlot.getAlignment() - (variableSize % varSlot.getAlignment());
            }
            variableSize += varSlot.getType().getByteSize();
        }

        // 计算参数区大小
        var argumentSize = 0;
        for (var slot : target.getStackFrame()) {
            if (slot instanceof IceStackSlot.ArgumentStackSlot argSlot) {
                argumentSize = Math.max(argumentSize, argSlot.getCallInstruction().getArguments().size());
            }
        }

        if (variableSize % 16 != 0) {
            // 对齐到 16 字节
            variableSize += 16 - (variableSize % 16);
        }

        if (argumentSize % 16 != 0) {
            // 对齐到 16 字节
            argumentSize += 16 - (argumentSize % 16);
        }
        stackSize += variableSize + argumentSize; // 对齐到 16 字节的栈帧大小

        var returnRegisterSize = 0;
        if (target.isHasCall()) { // 如果函数有调用外部函数，则需要保存返回地址
            returnRegisterSize = 2 * 8; // 大小为16字节依旧对齐/.
            stackSize += returnRegisterSize;
            if (argumentSize != 0) {
                // 有函数调用且有栈上参数 => 保存返回地址和帧指针
                if (isArithmeticImmediate(stackSize)) {
                    prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(stackSize)));
                    prologueList.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]", IceConstantData.create(argumentSize)));
                    prologueList.add(new ARM64Instruction("ADD x29, sp, {imm:stack}", IceConstantData.create(argumentSize)));

                    epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp, {imm:stack}]", IceConstantData.create(argumentSize)));
                    epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(stackSize)));
                } else {
                    // 处理大栈帧,循环SUB/ADD
                    int remainingSize = stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                    prologueList.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]", IceConstantData.create(argumentSize)));
                    prologueList.add(new ARM64Instruction("ADD x29, sp, {imm:stack}", IceConstantData.create(argumentSize)));

                    epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp, {imm:stack}]", IceConstantData.create(argumentSize)));
                    // 处理栈帧恢复
                    remainingSize = stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                }
            } else {
                // 有函数调用但没有栈上参数 => 只需要保存返回地址和帧指针
                if (isSTPImmediate(stackSize)) {
                    // 处理小的栈帧
                    prologueList.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]!", IceConstantData.create(-stackSize)));
                    prologueList.add(new ARM64Instruction("MOV x29, sp"));

                    epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp], {imm:stack}", IceConstantData.create(stackSize)));
                } else {
                    // 处理大的栈帧
                    if (isArithmeticImmediate(stackSize)) {
                        prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(stackSize)));
                        prologueList.add(new ARM64Instruction("STP x29, x30, [sp]"));

                        epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp]"));
                        epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(stackSize)));
                    } else {
                        // 处理大栈帧,循环SUB/ADD
                        int remainingSize = stackSize;
                        while (remainingSize > arithmeticImmediateUpperBound) {
                            prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                            remainingSize -= arithmeticImmediateUpperBound;
                        }
                        if (remainingSize > 0) {
                            prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                        }
                        prologueList.add(new ARM64Instruction("STP x29, x30, [sp]"));

                        epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp]"));
                        // 处理栈帧恢复
                        remainingSize = stackSize;
                        while (remainingSize > arithmeticImmediateUpperBound) {
                            epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                            remainingSize -= arithmeticImmediateUpperBound;
                        }
                        if (remainingSize > 0) {
                            epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                        }
                    }
                }

            }
        } else {
            // Leaf function => 只用分配栈空间
            if (stackSize > 0) {
                if (isArithmeticImmediate(stackSize)) {
                    prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(stackSize)));
                    prologueList.add(new ARM64Instruction("MOV x29, sp"));
                    
                    epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(stackSize)));
                } else {
                    // 处理大栈帧,循环SUB/ADD
                    int remainingSize = stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        prologueList.add(new ARM64Instruction("SUB sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                    prologueList.add(new ARM64Instruction("MOV x29, sp"));

                    // 处理栈帧恢复
                    remainingSize = stackSize;
                    while (remainingSize > arithmeticImmediateUpperBound) {
                        epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(arithmeticImmediateUpperBound)));
                        remainingSize -= arithmeticImmediateUpperBound;
                    }
                    if (remainingSize > 0) {
                        epilogueList.add(new ARM64Instruction("ADD sp, sp, {imm:stack}", IceConstantData.create(remainingSize)));
                    }
                }
            }
        }


        // Phase 2: entry 插入函数序言
        prologueList.forEach(instr -> instr.setParent(target.getEntryBlock()));
        target.getEntryBlock().addAll(0, prologueList);

        // Phase 3: 在每个块的末尾插入尾声
        for (var block : target) {
            if (block.successors().isEmpty()) {
                for (var i = block.size() - 1; i >= 0; i--) {
                    if (!block.get(i).isTerminal()) {
                        var clonedInstructions = epilogueList.stream().map(instr -> {
                            var cloned = instr.clone();
                            cloned.setParent(block);
                            return cloned;
                        }).toList();
                        block.addAll(i + 1, clonedInstructions);
                        break;
                    }
                }
            }
        }

        // Phase 4: 设置栈槽偏移量
        // 设置参数栈槽偏移量
        // FIXME: 这里写的不对
        for (var slot : target.getStackFrame()) {
            if (slot instanceof IceStackSlot.ArgumentStackSlot argSlot) {
                // 参数从栈顶开始向下分配
                assert argSlot.getArgumentIndex() > 8;

                int offset = ((argSlot.getArgumentIndex() - 8) * 8);
                argSlot.setOffset(offset);
            }
        }

        // 设置变量栈槽偏移量 - 使用已排序的变量列表
        var currentOffset = 0; // 变量从sp指针开始开始向高地址分配
        for (var varSlot : variableSlots) {
            // 对齐确保当前地址是对齐到要求的
            if (currentOffset % varSlot.getAlignment() != 0) {
                currentOffset += varSlot.getAlignment() - (currentOffset % varSlot.getAlignment());
            }
            varSlot.setOffset(currentOffset + argumentSize + returnRegisterSize); // 有为内部函数调用准备的栈帧和 lr + fp

            // 计算下一个变量的偏移量
            var currentVariableSize = varSlot.getType().getByteSize();
            currentOffset += currentVariableSize;
        }

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
