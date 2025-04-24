package top.voidc.e2e;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import top.voidc.e2e.environment.CompilerHelper;
import top.voidc.e2e.environment.Testcase;
import top.voidc.e2e.environment.TestcaseRunner;
import top.voidc.e2e.runner.LocalClangRunner;
import top.voidc.misc.Log;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CompilerTest {

    private static final TestcaseRunner runner = new LocalClangRunner();

    // 发现所有测试用例
    private static Stream<Testcase> provideTestcases() {
        final String ENV_NAME = "TESTCASE_DIR";
        String testcasePaths = System.getenv(ENV_NAME);

        if (testcasePaths == null || testcasePaths.isBlank()) {
            testcasePaths = "testcases/functional;testcases/h_functional";
            Log.i(String.format("环境变量 %s 未设置，使用默认路径: %s%n", ENV_NAME, testcasePaths));
        } else {
            Log.i(String.format("使用环境变量 %s = %s%n", ENV_NAME, testcasePaths));
        }

        // 支持多个路径（用 ; 分隔）
        String[] paths = testcasePaths.split(";");
        List<File> validDirs = Arrays.stream(paths)
                .map(String::trim)
                .map(File::new)
                .filter(file -> file.exists() && file.isDirectory())
                .toList();

        if (validDirs.isEmpty()) {
            throw new IllegalStateException("未找到有效的测试用例目录，请检查 TESTCASE_DIR 设置");
        }

        // 遍历每个目录下的文件，构造 testcases
        return validDirs.stream()
                .flatMap(dir -> {
                    File[] files = dir.listFiles();
                    if (files == null) return Stream.empty();

                    return Arrays.stream(files).sorted()
                            .map(CompilerTest::createTestcaseFromFilePath)
                            .filter(Optional::isPresent)
                            .map(Optional::get);
                });
    }

    private static Optional<Testcase> createTestcaseFromFilePath(File pathname) {
        String[] filenameSegment = pathname.getName().split("\\.");
        if (filenameSegment.length != 2) return Optional.empty();
        String name = filenameSegment[0];
        String ext = filenameSegment[1];
        if (!"sy".equals(ext)) return Optional.empty();

        File out = new File(pathname.getParentFile(), name + ".out");
        if (!out.exists()) return Optional.empty();

        File in = new File(pathname.getParentFile(), name + ".in");
        if (!in.exists()) in = null;

        File asm = new File(pathname.getParentFile(), name + ".s");

        return Optional.of(new Testcase(name, in, out, pathname, asm));
    }

    private static void compileSysySource(Testcase testcase, File output) throws InvocationTargetException, IllegalAccessException {
        final var args = List.of("-S", "-o", output.getAbsolutePath(), testcase.src().getAbsolutePath(),
                "-fenable-ptr-type");
        CompilerHelper.runMain(args);
    }

    @BeforeAll
    public static void setup() {
        // 编译 Libsysy
        Log.i("使用 " + runner.getName() + " 汇编器 " + runner.getAssemblerName());
        assertTrue(runner.beforeAll(), runner.getName() + " 的 beforeAll() 失败");
    }


    // 主测试方法：编译 + 检查输出文件存在
    @ParameterizedTest(name = "Compile: {0}")
    @MethodSource("provideTestcases")
    public void testCompile(Testcase testcase) throws InvocationTargetException, IllegalAccessException, IOException, InterruptedException {
        Log.i("开始编译 SysY 测试样例到汇编: " + testcase.name());
        CompilerHelper.runMain(runner.getCompileArgument(testcase));
        assertTrue(testcase.asm().exists(), "Assembly file not generated");
        // 生成并运行可执行文件

        Log.i(">>> 开始运行 SysY 测试样例: " + testcase.name());
        assertTrue(runner.prepareTest(testcase), "Prepare failed");

        Log.i(">>> 汇编 SysY 测试样例: " + testcase.name());
        assertTrue(runner.compileTest(testcase), "Assemble failed");

        Log.i(">>> 运行 SysY 测试样例: " + testcase.name());
        assertTrue(runner.runTest(testcase), "Run failed");

        Log.i(">>> 清理 SysY 测试样例: " + testcase.name());
        assertTrue(runner.cleanup(testcase), "Cleanup failed");

        assertTrue(testcase.asm().delete(), "Assembly file not deleted");
    }
}
