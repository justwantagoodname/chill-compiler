package top.voidc.misc;

import java.io.PrintStream;
import java.util.Arrays;
import java.util.stream.Collectors;

public class Log {
    private static final String ANSI_RESET = "\033[0m";
    private static final String ANSI_BLUE = "\033[34m";
    private static final String ANSI_RED = "\033[31m";
    private static final boolean DEBUG = Boolean.parseBoolean(System.getProperty("debug"));
    private static PrintStream OUT = System.out;

    public static void d(String format) {
        if (DEBUG) log(ANSI_BLUE + "DEBUG" + ANSI_RESET, format);
        else log("DEBUG", format);
    }

    public static void i(String format) {
        if (DEBUG) log(ANSI_BLUE + "INFO" + ANSI_RESET, format);
        else log("INFO", format);
    }

    public static void w(String format) {
        if (DEBUG) log(ANSI_RED + "WARN" + ANSI_RESET, ANSI_RED + format + ANSI_RESET);
        else log("WARN", format);
    }

    public static void e(String format) {
        if (DEBUG) log(ANSI_RED + "ERROR" + ANSI_RESET, ANSI_RED + format + ANSI_RESET);
        else log("ERROR", format);
    }

    private static void log(String level, String format) {
        StackTraceElement caller = Thread.currentThread().getStackTrace()[3];
        String className = caller.getClassName();
        String methodName = caller.getMethodName();
        String fileName = caller.getFileName();
        int lineNumber = caller.getLineNumber();
        final var prettyClass = Arrays.stream(className.split("\\."))
                .map(packageName -> packageName.substring(0, 1)).collect(Collectors.joining("."));
        OUT.println("[" + level + "][" + prettyClass + "." + methodName + "() @ (" + fileName + ":" + lineNumber + ")] " + format);
    }

    public static void should(boolean condition, String format) {
        if (!condition) {
            throw new AssertionError(format);
        }
    }

    public static void setOutputStream(PrintStream out) {
        Log.OUT = out;
    }
}