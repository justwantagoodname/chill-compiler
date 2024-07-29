package top.voidc.misc;

public class Log {
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BLUE = "\033[34m";
    private static final String ANSI_RED = "\033[31m";
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("debug"));

    public static void d(String format, Object... args) {
        if (DEBUG) log(ANSI_BLUE + "DEBUG" + ANSI_RESET, format, args);
        else log("DEBUG", format, args);
    }

    public static void e(String format, Object... args) {
        if (DEBUG) log(ANSI_RED + "ERROR" + ANSI_RESET, format, args);
        else log("ERROR", format, args);
    }

    private static void log(String level, String format, Object... args) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
        String className = caller.getClassName();
        String methodName = caller.getMethodName();
        String fileName = caller.getFileName();
        int lineNumber = caller.getLineNumber();
        String message = String.format(format, args);
        System.out.printf("[%s][%s.%s() at: %sL%d] %s%n", level, className, methodName, fileName, lineNumber, message);
    }
}