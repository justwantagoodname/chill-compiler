package top.voidc.misc;

public class Log {
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BLUE = "\033[34m";
    private static final String ANSI_RED = "\033[31m";
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("debug"));

    public static void d(String format) {
        if (DEBUG) log(ANSI_BLUE + "DEBUG" + ANSI_RESET, format);
        else log("DEBUG", format);
    }

    public static void e(String format, Object... args) {
        if (DEBUG) log(ANSI_RED + "ERROR" + ANSI_RESET, ANSI_RED + format + ANSI_RESET);
        else log("ERROR", format);
    }

    private static void log(String level, String format) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
        String className = caller.getClassName();
        String methodName = caller.getMethodName();
        String fileName = caller.getFileName();
        int lineNumber = caller.getLineNumber();
        System.out.printf("[%s][%s.%s() at: %sL%d] %s%n", level, className, methodName, fileName, lineNumber, format);
    }

    public static void should(boolean condition, String format) {
        if (!condition) {
            throw new AssertionError(format);
        }
    }
}