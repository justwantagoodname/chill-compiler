package top.voidc.e2e.environment;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;

/**
 * 由于 CG 要求 Compiler 主类不能带包名，所用反射来调用主类，分离相关代码了
 */
public class CompilerHelper {
    // 动态加载编译器类
    private static final Class<?> SY_COMPILER;
    private static final Method SY_COMPILER_MAIN;

    static {
        try {
            SY_COMPILER = Class.forName("Compiler");
            SY_COMPILER_MAIN = SY_COMPILER.getMethod("main", String[].class);
        } catch (Exception e) {
            throw new RuntimeException("Can't find Compiler class");
        }
    }

    public static void runMain(List<String> compilerOptions)
            throws InvocationTargetException, IllegalAccessException {
        String[] args = compilerOptions.toArray(new String[0]);
        SY_COMPILER_MAIN.invoke(null, (Object) args);
    }
}
