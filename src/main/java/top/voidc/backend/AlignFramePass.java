package top.voidc.backend;

import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

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
}
