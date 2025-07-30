package top.voidc.optimizer.pass.function;

import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

/**
 * 按顺序重命名函数内的匿名寄存器，确保递增
 */
@Pass(group = {"O0"}, parallel = true)
public class RenameVariable implements CompilePass<IceFunction> {
    private int variableCount = 0;

    @Override
    public String getName() {
        return "RenameVariable";
    }

    @Override
    public boolean run(IceFunction target) {
        renameVariable(target);
        return true;
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
        function.blocks().forEach(block -> block.forEach(instruction -> {
            if (canRename(instruction.getName())) {
                instruction.setName(String.valueOf(variableCount++));
            }
        }));
    }
}
