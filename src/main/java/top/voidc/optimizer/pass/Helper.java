package top.voidc.optimizer.pass;

import top.voidc.ir.IceBlock;
import top.voidc.ir.ice.instruction.IceInstruction;
import top.voidc.ir.ice.instruction.IcePHINode;

public class Helper {
    /**
     * 从 CFG 上删除一个 block
     *
     * @deprecated
     * @see IceBlock#destroy()
     * @param block 要删除的 block
     */
    @Deprecated
    public static void removeBlock(IceBlock block) {
        block.destroy();
    }
}
