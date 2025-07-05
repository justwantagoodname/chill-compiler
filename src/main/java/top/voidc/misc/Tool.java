package top.voidc.misc;

public class Tool {
    public static void TODO(String reason) {
        throw new UnsupportedOperationException("Not implemented yet: " + reason);
    }

    public static boolean inRange(int val, int min, int max) {
        return val >= min && val <= max;
    }
}
