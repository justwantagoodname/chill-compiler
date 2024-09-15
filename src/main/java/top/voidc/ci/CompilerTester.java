package top.voidc.ci;

import java.io.File;
import java.io.PrintStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.*;

public class CompilerTester {
    public static boolean ARM = false;
    public static boolean RV = false;

    public record Testcase(String name, File in, File out, File src) {
        @Override
        public String toString() {
            return "Testcase{" +
                    "name='" + name + '\'' +
                    '}';
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
        private ResultStatus status;

        @Override
        public String toString() {
            return "TestResult{" +
                    "testcase=" + testcase +
                    ", status=" + status +
                    '}';
        }

        public TestResult(Testcase testcase) {
            this.testcase = testcase;
            this.status = ResultStatus.RUNNING;
            this.asm = new File(testcase.src.getParentFile(), testcase.name() + ".s");
            this.actualOutput = new File(testcase.src.getParentFile(), testcase.name() + ".actual.out");
            this.compilerOutput = new File(testcase.src.getParentFile(), testcase.name() + ".compiler.log");
        }

        public void cleanup() {
            getAsm().delete();
            getActualOutput().delete();
            getCompilerOutput().delete();
        }

        public ResultStatus getStatus() {
            return status;
        }

        public void setStatus(ResultStatus status) {
            this.status = status;
        }

        public Testcase getTestcase() {
            return testcase;
        }

        public File getCompilerOutput() {
            return compilerOutput;
        }

        public File getActualOutput() {
            return actualOutput;
        }

        public File getAsm() {
            return asm;
        }
    }

    // BYD CG ONLY SOLUTION.
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

    public static Optional<Testcase> createTestcaseFromFilePath(File pathname) {
        final var filenameSegment = pathname.getName().split("\\.");
        assert filenameSegment.length == 2;
        final var testCaseName = filenameSegment[0];
        final var extName = filenameSegment[1];
        final var outputData = new File(pathname.getParentFile(), testCaseName + "." + "out");
        if (pathname.isDirectory() || !("sy".equals(extName)) || !outputData.exists()) {
            return Optional.empty();
        }
        var inputData = new File(pathname.getParentFile(), testCaseName + ".in");
        if (!inputData.exists()) inputData = null;
        return Optional.of(new Testcase(testCaseName, inputData, outputData, pathname));
    }

    public static void compileSysySource(Testcase testcase, File output) throws InvocationTargetException, IllegalAccessException {
        assert testcase.src != null;

        final var args = new String[]{"-S", "-o", output.getAbsolutePath(), testcase.src.getAbsolutePath()};
        SY_COMPILER_MAIN.invoke(null, (Object) args);
    }

    public static void main(String[] args) throws Exception {
        System.out.println("Starting Testing...");
        assert args.length == 2;

        switch (args[0].toUpperCase()) {
            case "ARM":
                ARM = true;
                break;
            case "RISCV":
                RV = true;
                break;
            default:
                assert false;
                break;
        }

        System.out.println("=== [Phase] Finding testcases ===");
        final var testcaseFolder = new File(args[1]);

        assert testcaseFolder.exists() && testcaseFolder.isDirectory();

        final var testcases = Arrays.stream(Objects.requireNonNull(testcaseFolder.listFiles())).parallel()
                .map(CompilerTester::createTestcaseFromFilePath)
                .filter(Optional::isPresent).map(Optional::get).toList();

        System.out.printf("%d Testcases Found! \n", testcases.size());

        final var testResults = testcases.stream().map(TestResult::new).toList();

        System.out.println("Cleanup Done");

        System.out.println("=== [Phase] Cleanup ===");

        testResults.parallelStream().forEach(TestResult::cleanup);

        System.out.println("=== [Phase] Compile sysy source(s) ===");
        final var originalOut = System.out;
        final var originalErr = System.err;
        testResults.forEach(result -> {
            try (final var logFileStream = new PrintStream(result.getCompilerOutput())) {
                System.setErr(logFileStream);
                System.setOut(logFileStream);

                try {
                    compileSysySource(result.getTestcase(), result.getAsm());
                } catch (Exception e) {
                    // NOTE: Redirect System.err here. shouldn't move out.
                    e.printStackTrace();
                } finally {
                    if (!result.getAsm().exists()) {
                        result.setStatus(ResultStatus.CE);
                    }
                }
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        System.setOut(originalOut);
        System.setErr(originalErr);

        final var CESize = testResults.parallelStream()
                .filter(testResult -> testResult.getStatus() == ResultStatus.CE).count();

        System.out.println();

        if (CESize > 0) {
            throw new Exception(CESize + " testcase(s) compile failed. Abort.");
        }
    }
}
