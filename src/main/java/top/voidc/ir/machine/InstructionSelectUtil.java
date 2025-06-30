package top.voidc.ir.machine;

import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceConstantData;

public class InstructionSelectUtil {
    public static boolean isReg(IceValue value) {
        return !(value instanceof IceConstantData);
    }
}
