package top.voidc.backend.peephole;

import top.voidc.ir.machine.IceMachineInstruction;

import java.util.List;

public interface PeepholePattern {
    int getWindowSize();
    /**
     * 匹配并应用模式到指令列表
     * 返回 null 表示不匹配或不需要应用
     * 返回新的指令列表表示匹配成功并应用了模式
     * @param instructions 指令列表
     * @return 应用模式后的指令列表
     */
    default List<IceMachineInstruction> matchAndApply(List<IceMachineInstruction> instructions) {
        return null;
    }
}
