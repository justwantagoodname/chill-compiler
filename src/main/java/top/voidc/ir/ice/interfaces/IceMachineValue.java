package top.voidc.ir.ice.interfaces;

import top.voidc.ir.ice.type.IceType;

/**
 * 可以作为机器指令的操作数
 */

public interface IceMachineValue {
    String getReferenceName(boolean withType);

    void getTextIR(StringBuilder builder);

    IceType getType();
}
