package top.voidc.e2e.runner;

import top.voidc.e2e.environment.ProcessHelper;
import top.voidc.e2e.environment.Testcase;
import top.voidc.e2e.environment.TestcaseRunner;
import top.voidc.misc.Log;

import java.io.*;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 本地的 Clang IR 测试样例运行器
 * 需要保证本地安装了 Clang 17 以上和 GNU Make 工具
 * 主要用于测试编译器前端的编译和运行
 * **出于对运行速度和环境一致性的考虑，本地运行器仅支持 Linux 本地测试，非 Linux 使用 SSHGNURunner 代替**
 */
public class LocalClangRunner implements TestcaseRunner {

    private static class TestResult {
        private final Testcase testcase;
        private final File actualOutput;
        private final File irOutput;
        private final File executableOutput;

        public TestResult(Testcase testcase) {
            this.testcase = testcase;
            this.actualOutput = new File(testcase.src().getParentFile(), testcase.name() + ".actual.out");
            this.irOutput = new File(testcase.src().getParentFile(), testcase.name() + ".ll");
            this.executableOutput = new File(testcase.src().getParentFile(), testcase.name() + ".exe");
        }

        public void cleanup() {
            actualOutput.delete();
            irOutput.delete();
            executableOutput.delete();
        }

        public File getExecutableOutput() {
            return executableOutput;
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

    private final Map<String, TestResult> testResultMap = new HashMap<>();

    @Override
    public String getName() {
        return System.getProperty("os.name") + " LocalRunner";
    }

    @Override
    public String getAssemblerName() {
        return "clang";
    }

    public void compileLibsysy() throws IOException, InterruptedException {
        Log.i("Compiling Libsysy");

        final var libsysyDir = new File("testcases/libsysy");
        final var libsysy = new File(libsysyDir, "libsysy.a");

        if (libsysy.exists()) {
            return;
        }

        final var buildResult = ProcessHelper.execute(libsysyDir, "make", "-f", "Makefile", "build");

        if (!buildResult.isSuccess()) {
            Log.e("Make Output:\n===\n" + buildResult.stdout() + "===\n===\n"+ buildResult.stderr());
            throw new RuntimeException("Libsysy compilation failed: make exit code = " + buildResult.exitCode());
        }

        if (!libsysy.exists()) {
            throw new AssertionError("Libsysy doesn't exist");
        }

        Log.i("Libsysy compiled successfully: " + libsysy.getAbsolutePath());
    }

    public static void compileToExecutable(File llvmFile, File output) throws IOException, InterruptedException {
        if (llvmFile == null || !llvmFile.exists()) {
            throw new IllegalArgumentException("File does not exist: " + llvmFile);
        }

        final var clangResult = ProcessHelper.execute("clang", "-x", "ir", "-Ltestcases/libsysy", "-lsysy", "-lc++",
                "-o", output.getAbsolutePath(), llvmFile.getAbsolutePath());

        if (!clangResult.isSuccess()) {
            Log.e("IR verification failed:\n===LLVM OUTPUT===\n" + clangResult.stderr() + "===LLVM END===\n");
            throw new RuntimeException("Compilation failed: clang exit code = " + clangResult.exitCode());
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
            throw new AssertionError("Output mismatch for testcase: " + result.getTestcase().name());
        }
    }

    @Override
    public List<String> getCompileArgument(Testcase test) {
        return List.of("-o", test.asm().getAbsolutePath(),
                test.src().getAbsolutePath(),
                "-fenable-ptr-type", "-S");
    }

    @Override
    public boolean compileTest(Testcase test) throws IOException, InterruptedException {
        final var testResult = testResultMap.get(test.name());
        Log.i("Clang Compiling");
        compileToExecutable(testResult.getIrOutput(), testResult.getExecutableOutput());
        Log.i("Compiled: " + testResult.getExecutableOutput().getAbsolutePath());
        return true;
    }

    @Override
    public boolean beforeAll() throws IOException, InterruptedException {
        compileLibsysy();
        return true;
    }

    @Override
    public boolean prepareTest(Testcase test) {
        testResultMap.put(test.name(), new TestResult(test));
        return true;
    }

    @Override
    public boolean runTest(Testcase test) throws IOException, InterruptedException {
        final var testResult = testResultMap.get(test.name());
        Log.i("Running " + testResult.getExecutableOutput().getAbsolutePath());
        runExecutableAndCompare(testResult);
        Log.i("Run End");
        return true;
    }

    @Override
    public boolean cleanup(Testcase test) throws IOException, InterruptedException {
        testResultMap.get(test.name()).cleanup();
        return true;
    }
}
