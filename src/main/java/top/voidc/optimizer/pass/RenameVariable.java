package top.voidc.optimizer.pass;

import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;

/**
 * 按顺序重命名函数内的匿名寄存器，确保递增
 */
@Pass
public class RenameVariable implements CompilePass {

    private final IceUnit unit;
    private int variableCount = 0;

    public RenameVariable(IceContext context) {
        this.unit = context.getCurrentIR();
    }

    @Override
    public String getName() {
        return "RenameVariable";
    }

    @Override
    public void run() {
        unit.getFunctions().forEach(this::renameVariable);
    }

    /**
     * 变量是由纯数字构成的就能
     * @param varName 变量名
     */
    private boolean canRename(String varName) {
        if (varName == null || varName.isEmpty()) return false;
        for (int i = 0; i < varName.length(); i++) {
            if (!Character.isDigit(varName.charAt(i))) return false;
        }
        return true;
    }

    private void renameVariable(IceFunction function) {
        variableCount = 0;
        function.blocks().forEach(block -> {
            block.instructions().forEach(instruction -> {
                if (canRename(instruction.getName())) {
                    instruction.setName(String.valueOf(variableCount++));
                }
            });
        });
    }
}
