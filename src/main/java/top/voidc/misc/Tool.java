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
}
