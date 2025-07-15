package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceConstantInt;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceMachineInstruction;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 对齐栈帧并设置偏移量，同时插入函数序言和尾声
 */
@Pass(group = {"O0", "backend"})
public class AlignFramePass implements CompilePass<IceMachineFunction>, IceArchitectureSpecification {
    @Override
    public boolean run(IceMachineFunction target) {
        var prologueList = new ArrayList<IceMachineInstruction>();
        var epilogueList = new ArrayList<IceMachineInstruction>();

        var base = new AtomicInteger(0);
        target.getStackFrame().forEach(slot -> {
            slot.setOffset(base.get());
            base.addAndGet(slot.getType().getByteSize()); // TODO: 后序需要修改按照 align 和AAPCS64要求对齐
        });

        if (target.isHasCall()) {
            // 如果函数有调用外部函数，则需要保存返回地址
            prologueList.add(new ARM64Instruction("STP x29, x30, [sp, {imm:stack}]!", IceConstantData.create(32)));
            epilogueList.add(new ARM64Instruction("LDP x29, x30, [sp], {imm:stack}", IceConstantData.create(32)));
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

//    public boolean run(IceMachineFunction function) {
//        // 1. 收集所有栈槽
//        List<IceStackSlot> slots = function.getStackFrame();
//
//        // 2. 计算总栈大小（考虑对齐）
//        int totalSize = 0;
//        for (IceStackSlot slot : slots) {
//            // 对齐当前偏移量
//            totalSize = align(totalSize, slot.getAlignment());
//            slot.setOffset(totalSize);
//            totalSize += slot.getType().getByteSize();
//        }
//
//        // 3. 对齐总栈大小到16字节
//        totalSize = align(totalSize, 16);
//
//        // 4. 在入口块插入栈调整指令
//        IceBlock entry = function.getMachineBlock("entry");
//        entry.addInstructionAtFront(new ARM64Instruction("SUB sp, sp, #" + totalSize));
//
//        // 5. 在返回块恢复栈指针
//        function.getExitBlock().addInstruction(new ARM64Instruction("ADD sp, sp, #" + totalSize));
//
//        return true;
//    }
//
//    private int align(int offset, int alignment) {
//        return (offset + alignment - 1) & ~(alignment - 1);
//    }
}
