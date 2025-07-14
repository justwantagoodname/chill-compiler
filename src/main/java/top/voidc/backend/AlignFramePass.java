package top.voidc.backend;

import top.voidc.backend.arm64.instr.ARM64Instruction;
import top.voidc.ir.IceBlock;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.ir.machine.IceStackSlot;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Pass(group = {"O0", "backend"})
public class AlignFramePass implements CompilePass<IceMachineFunction> {
    @Override
    public boolean run(IceMachineFunction target) {
        var base = new AtomicInteger(0);
        target.getStackFrame().forEach(slot -> {
            slot.setOffset(base.get());
            base.addAndGet(slot.getType().getByteSize()); // TODO: 后序需要修改按照 align 和AAPCS64要求对齐
        });
        return true;
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
