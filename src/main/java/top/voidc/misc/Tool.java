package top.voidc.misc;

public class Tool {
    public static void TODO(String reason) {
        throw new UnsupportedOperationException("Not implemented yet: " + reason);
    }

    public static boolean inRange(int val, int min, int max) {
        return val >= min && val <= max;
    }

    /**
     * 计算整数的二进制对数
     * @author GitHub Copilot
     * @param value 要计算的值，必须为正整数
     * @return 二进制对数
     */
    public static int log2(int value) {
        if (value <= 0) {
            throw new IllegalArgumentException("Value must be positive");
        }
        return 31 - Integer.numberOfLeadingZeros(value);
    }

    /**
     * 检查一个整数是否是2的幂
     * @param value 要检查的值，必须为正整数
     * @return 如果是2的幂返回true，否则返回false
     */
    public static boolean isPowerOfTwo(int value) {
        return value > 0 && (value & (value - 1)) == 0;
    }
}
