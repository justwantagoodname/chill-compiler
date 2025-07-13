package top.voidc.ir.ice.interfaces;

public interface IceAlignable {
    /**
     * 获取对齐大小
     * @return 对齐大小，对齐到 n 字节
     */
    int getAlignment();

    /**
     * 设置对齐大小
     * @param alignment 对齐大小
     */
    default void setAlignment(int alignment) {
        throw new UnsupportedOperationException();
    }
}
