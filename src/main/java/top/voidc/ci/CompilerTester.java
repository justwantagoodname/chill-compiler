package top.voidc.ci;

import java.io.File;
import java.io.FileFilter;
import java.util.Arrays;
import java.util.Objects;

public class CompilerTester {
    public static boolean ARM = false;
    public static boolean RV = false;
    public record Testcase(File in, File out, File src) {}

    public static void main(String[] args) {
        System.out.println("Starting Testing...");
        assert args.length == 2;

        switch (args[0].toUpperCase()) {
            case "ARM": ARM = true; break;
            case "RISCV":  RV = true; break;
            default: assert false; break;
        }

        final var testcaseFolder = new File(args[1]);

        assert testcaseFolder.exists() && testcaseFolder.isDirectory();

        final var testcasesFiles = testcaseFolder.listFiles(pathname -> {
            final var filenameSegment = pathname.getName().split("\\.");
            assert filenameSegment.length == 2;
            final var testCaseName = filenameSegment[0];
            final var extName = filenameSegment[1];
            final var outputData = new File(testcaseFolder, testCaseName + "." + "out");
            return !pathname.isDirectory() && "sy".equals(extName) && outputData.exists();
        });

        assert testcasesFiles != null;

        final var testcases = Arrays.stream(testcasesFiles).map(testcase -> {
            final var filenameSegment = testcase.getName().split("\\.");
            assert filenameSegment.length == 2;
            final var testCaseName = filenameSegment[0];
            var inputData = new File(testcaseFolder, testCaseName + "." + "in");
            final var outputData = new File(testcaseFolder, testCaseName + "." + "out");
            if (!inputData.exists()) inputData = null;
            return new Testcase(inputData, outputData, testcase);
        }).toList();

    }
}
