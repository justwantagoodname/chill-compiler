package top.voidc.ci;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import top.voidc.misc.Log;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

public class CompilerTest {
    public record Testcase(String name, File in, File out, File src) {
        @Override
        public String toString() {
            return name;
        }
    }

    public enum ResultStatus {
        RUNNING, AC, WA, CE, RE, TLE
    }

    public static class TestResult {
        private final Testcase testcase;
        private final File asm;
        private final File actualOutput;
        private final File compilerOutput;
        private final File irOutput;
        private ResultStatus status;

        public TestResult(Testcase testcase) {
            this.testcase = testcase;
            this.status = ResultStatus.RUNNING;
            this.asm = new File(testcase.src.getParentFile(), testcase.name() + ".s");
            this.actualOutput = new File(testcase.src.getParentFile(), testcase.name() + ".actual.out");
            this.compilerOutput = new File(testcase.src.getParentFile(), testcase.name() + ".compiler.log");
            this.irOutput = new File(testcase.src.getParentFile(), testcase.name() + ".ll");
        }

        public void cleanup() {
            asm.delete();
            actualOutput.delete();
            compilerOutput.delete();
            irOutput.delete();
        }

        public ResultStatus getStatus() {
            return status;
        }

        public void setStatus(ResultStatus status) {
            this.status = status;
        }

        public File getAsm() {
            return asm;
        }

        public File getCompilerOutput() {
            return compilerOutput;
        }

        public File getActualOutput() {
            return actualOutput;
        }

        public File getIrOutput() {
            return irOutput;
        }

        public Testcase getTestcase() {
            return testcase;
        }
    }

    // 动态加载编译器类
    public static Class<?> SY_COMPILER;
    public static Method SY_COMPILER_MAIN;

    static {
        try {
            SY_COMPILER = Class.forName("Compiler");
            SY_COMPILER_MAIN = SY_COMPILER.getMethod("main", String[].class);
        } catch (Exception e) {
            throw new AssertionError("Can't find Compiler class");
        }
    }

    // 发现所有测试用例
    public static Stream<Testcase> provideTestcases() {
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

                    return Arrays.stream(files)
                            .map(CompilerTest::createTestcaseFromFilePath)
                            .filter(Optional::isPresent)
                            .map(Optional::get);
                });
    }

    public static Optional<Testcase> createTestcaseFromFilePath(File pathname) {
        String[] filenameSegment = pathname.getName().split("\\.");
        if (filenameSegment.length != 2) return Optional.empty();
        String name = filenameSegment[0];
        String ext = filenameSegment[1];
        if (!"sy".equals(ext)) return Optional.empty();

        File out = new File(pathname.getParentFile(), name + ".out");
        if (!out.exists()) return Optional.empty();

        File in = new File(pathname.getParentFile(), name + ".in");
        if (!in.exists()) in = null;

        return Optional.of(new Testcase(name, in, out, pathname));
    }

    public static void compileSysySource(Testcase testcase, File output) throws InvocationTargetException, IllegalAccessException {
        String[] args = {"-S", "-o", output.getAbsolutePath(), testcase.src.getAbsolutePath()};
        SY_COMPILER_MAIN.invoke(null, (Object) args);
    }

    public static boolean verifyIR(File llvmFile) {
        if (llvmFile == null || !llvmFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + llvmFile);
        }

        ProcessBuilder builder = new ProcessBuilder();
        builder.command("opt", "-passes=verify", llvmFile.getAbsolutePath(), "-o", "-");

        try {
            Process process = builder.start();

            // 获取错误输出
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
            StringBuilder errors = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errors.append(line).append("\n");
            }

            int exitCode = process.waitFor();
            if (exitCode == 0) {
                return true;  // 验证成功
            } else {
                System.err.println("IR verification failed:\n===LLVM OUTPUT===" + errors + "===LLVM END===");
                return false;
            }

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
            return false;
        }
    }

    // 主测试方法：编译 + 检查输出文件存在
    @ParameterizedTest(name = "Compile: {0}")
    @MethodSource("provideTestcases")
    public void testCompile(Testcase testcase) {
        TestResult result = new TestResult(testcase);
        try (PrintStream logStream = new PrintStream(result.getCompilerOutput())) {
            Log.setOutputStream(logStream); // 可选，记录日志
            compileSysySource(testcase, result.getAsm());
            assertTrue(result.getAsm().exists(), "Assembly file not generated");
            assertTrue(verifyIR(result.getIrOutput()), "LLVM IR Format Error");
        } catch (Exception e) {
            result.setStatus(ResultStatus.CE);
            fail("Compiling (" + testcase.src.getAbsolutePath()
                    + ") failed on: " + e.getMessage());
        } finally {
            result.cleanup();
        }
    }
}
