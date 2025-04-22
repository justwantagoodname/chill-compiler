package top.voidc.ci;

import org.junit.jupiter.api.BeforeAll;
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
        private final File executableOutput;
        private ResultStatus status;

        public TestResult(Testcase testcase) {
            this.testcase = testcase;
            this.status = ResultStatus.RUNNING;
            this.asm = new File(testcase.src.getParentFile(), testcase.name() + ".s");
            this.actualOutput = new File(testcase.src.getParentFile(), testcase.name() + ".actual.out");
            this.compilerOutput = new File(testcase.src.getParentFile(), testcase.name() + ".compiler.log");
            this.irOutput = new File(testcase.src.getParentFile(), testcase.name() + ".ll");
            this.executableOutput = new File(testcase.src.getParentFile(), testcase.name() + ".exe");
        }

        public void cleanup() {
            asm.delete();
            actualOutput.delete();
            compilerOutput.delete();
            irOutput.delete();
            executableOutput.delete();
        }

        public File getExecutableOutput() {
            return executableOutput;
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

                    return Arrays.stream(files).sorted()
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
        String[] args = {"-S", "-o", output.getAbsolutePath(), testcase.src.getAbsolutePath(), "-fenable-ptr-type"};
        SY_COMPILER_MAIN.invoke(null, (Object) args);
    }

    public static void compileToExecutable(File llvmFile, File output) throws IOException, InterruptedException {
        if (llvmFile == null || !llvmFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + llvmFile);
        }

        ProcessBuilder clangBuilder = new ProcessBuilder();
        clangBuilder.command("clang", "-x", "ir", "-Ltestcases/libsysy", "-lsysy", "-lc++",
                "-o", output.getAbsolutePath(), llvmFile.getAbsolutePath());

        Process clangProcess = clangBuilder.start();

        int clangExitCode = clangProcess.waitFor();

        if (clangExitCode != 0) {
            // 获取错误输出
            BufferedReader errorReader = new BufferedReader(new InputStreamReader(clangProcess.getErrorStream()));
            StringBuilder errors = new StringBuilder();
            String line;
            while ((line = errorReader.readLine()) != null) {
                errors.append(line).append("\n");
            }

            Log.e("IR verification failed:\n===LLVM OUTPUT===\n" + errors + "===LLVM END===\n");

            throw new RuntimeException("Compilation failed: clang exit code = " + clangExitCode);
        }
    }

    public static void runExecutableAndCompare(TestResult result) throws IOException, InterruptedException {
        File executable = result.getExecutableOutput();
        if (!executable.exists()) {
            throw new IllegalStateException("Executable file does not exist: " + executable);
        }
        
        // 首先运行程序并获取标准输出
        ProcessBuilder builder = new ProcessBuilder(executable.getAbsolutePath());
        builder.redirectOutput(result.getActualOutput());
        
        // 如果有输入文件，将其内容作为输入
        if (result.getTestcase().in() != null) {
            builder.redirectInput(result.getTestcase().in());
        }
        
        Process process = builder.start();
        int exitCode = process.waitFor();

        // Libsysy 的计时信息是通过 stderr打印的
        BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));
        StringBuilder errors = new StringBuilder();
        String line;
        while ((line = errorReader.readLine()) != null) {
            errors.append(line).append("\n");
        }

        if (!errors.isEmpty()) Log.i("libsysy 输出:\n===SysY OUTPUT===\n" + errors + "===SysY END===\n");

        // 将返回值追加到输出文件
        appendCode(exitCode, result.getActualOutput());

        // 比较输出
        compareOutput(result);
    }

    public static void appendCode(int code, File file) throws IOException {
        boolean endsWithNewline = true;

        // 判断最后一个字符是否是 '\n'
        if (file.exists() && file.length() > 0) {
            try (RandomAccessFile raf = new RandomAccessFile(file, "r")) {
                raf.seek(file.length() - 1);
                int lastByte = raf.read();
                endsWithNewline = (lastByte == '\n');
            }
        }

        // 追加写入 code（带换行）
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            if (!endsWithNewline) {
                writer.newLine();
            }
            writer.write(Integer.toString(code));
            writer.newLine(); // 保持追加后仍是换行结尾
        }
    }
    
    public static void compareOutput(TestResult result) throws IOException {
        List<String> expectedLines = Files.readAllLines(result.getTestcase().out().toPath());
        List<String> actualLines = Files.readAllLines(result.getActualOutput().toPath());
        
        if (!expectedLines.equals(actualLines)) {
            result.setStatus(ResultStatus.WA);
            throw new AssertionError("Output mismatch for testcase: " + result.getTestcase().name());
        }
        
        result.setStatus(ResultStatus.AC);
    }

    @BeforeAll
    public static void compileLibsysy() {
        Log.i("Compiling Libsysy");
        final var libsysyDir = new File("testcases/libsysy");
        final var libsysySrc = new File(libsysyDir, "sylib.c");
        final var libsysyTimerSrc = new File(libsysyDir, "sytimer.cc");
        final var libsysyOutput = new File(libsysyDir, "sylib.o");
        final var libsysyTimerOutput = new File(libsysyDir, "sytimer.o");
        final var libsysy = new File(libsysyDir, "libsysy.a");

        if (libsysy.exists()) {
            return;
        }

        if (!libsysyDir.exists()) {
            throw new IllegalStateException("Libsysy directory does not exist: " + libsysyDir);
        }

        if (!libsysySrc.exists() || !libsysyTimerSrc.exists()) {
            throw new IllegalStateException("Libsysy source file does not exist: " + libsysySrc);
        }

        final var clangBuilder = new ProcessBuilder();
        clangBuilder.command("clang", "-c", libsysySrc.getAbsolutePath(), "-o", libsysyOutput.getAbsolutePath());
        final var clangppBuilder = new ProcessBuilder();
        clangppBuilder.command("clang++", "-c", libsysyTimerSrc.getAbsolutePath(), "-o", libsysyTimerOutput.getAbsolutePath());
        final var arBuilder = new ProcessBuilder();
        arBuilder.command("ar", "-r", libsysy.getAbsolutePath(), libsysyOutput.getAbsolutePath(), libsysyTimerOutput.getAbsolutePath());

        try {
            final var process = clangBuilder.start();
            int exitCode = process.waitFor();
            if (exitCode != 0) {
                throw new RuntimeException("Libsysy compilation failed: clang exit code = " + exitCode);
            }
            Log.i("Libsysy compiled successfully: " + libsysyOutput.getAbsolutePath());
            // 运行 ar 命令
            final var arProc = arBuilder.start();
            int arExitCode = arProc.waitFor();
            if (arExitCode != 0) {
                throw new RuntimeException("Libsysy archive creation failed: ar exit code = " + arExitCode);
            }
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException("Error during libsysy compilation", e);
        }
    }

    // 主测试方法：编译 + 检查输出文件存在
    @ParameterizedTest(name = "Compile: {0}")
    @MethodSource("provideTestcases")
    public void testCompile(Testcase testcase) throws InvocationTargetException, IllegalAccessException, IOException, InterruptedException {
        TestResult result = new TestResult(testcase);
        compileSysySource(testcase, result.getAsm());
        assertTrue(result.getAsm().exists(), "Assembly file not generated");
        // 生成并运行可执行文件
        Log.i("Clang Compiling");
        compileToExecutable(result.getIrOutput(), result.getExecutableOutput());
        Log.i("Compiled: " + result.getExecutableOutput().getAbsolutePath());
        Log.i("Running " + result.getExecutableOutput().getAbsolutePath());
        runExecutableAndCompare(result);
        Log.i("Run End");

        result.cleanup();
    }
}
