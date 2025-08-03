package top.voidc.e2e.runner;

import com.jcraft.jsch.SftpException;
import top.voidc.e2e.environment.Testcase;
import top.voidc.misc.Log;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SSHIRRunner extends SSHRunner {

    public SSHIRRunner(String baseDir, SSHConfig sshConfig) {
        super(baseDir, sshConfig);
    }

    @Override
    public List<String> getCompileArgument(Testcase test) {
        return List.of(test.src().getAbsolutePath(), "-o", test.asm().getAbsolutePath(), "-S", "-emit-ir", "-fenable-ptr-type",
                "-fdisable-group", "backend");
    }

    /**
     * 上传文件到远程服务器
     * @param test 测试样例
     * @return 是否上传成功
     */
    @Override
    public boolean prepareTest(Testcase test) {
        final var result = new TestResult(test);
        testResultMap.put(test.name(), result);

        if (test.in() != null && test.in().exists() && !remoteFileExists(result.getRemoteInputPath())) {
            try {
                uploadFile(test.in(), result.getRemoteInputPath());
            } catch (Exception e) {
                e.printStackTrace();
                return false;
            }
        }

        Log.d("上传IR代码: " + result.getRemoteInputPath());

        assertTrue(result.getIrOutput().exists());

        try {
            uploadFile(result.getIrOutput(), result.getRemoteIrPath());
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean compileTest(Testcase test) {
        final var testResult = testResultMap.get(test.name());

        final var remoteLibsysyDir = workingDirectory + "/libsysy";

        try {
            final var clangResult = executeCommand("clang", "-x", "ir", "-S", "-fno-integrated-as", "-O2",
                    "-o", testResult.getRemoteAsmPath(), testResult.getRemoteIrPath());

            if (!clangResult.isSuccess()) {
                Log.e("IR verification failed:\n===LLVM OUTPUT===\n" + clangResult.stderr() + "\n===LLVM END===\n");
                Log.e("Compilation failed: clang exit code = " + clangResult.exitCode());
                return false;
            }

            final var gccResult = executeCommand("gcc", "-o", testResult.getRemoteExecutablePath(),
                    testResult.getRemoteAsmPath(), "-L" + remoteLibsysyDir, "-lsysy", "-lstdc++");
            if (!gccResult.isSuccess()) {
                Log.e("GCC compilation failed:\n===GCC OUTPUT===\n" + gccResult.stderr() + "\n===GCC END===\n");
                Log.e("Compilation failed: gcc exit code = " + gccResult.exitCode());
                return false;
            }

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
        return true;
    }

    @Override
    public boolean cleanup(Testcase test) {
        final var testResult = testResultMap.get(test.name());

        assertTrue(testResult.getActualOutput().delete());
        assertTrue(testResult.getIrOutput().delete());

        final var targetList = List.of(
                testResult.getRemoteIrPath(),
                testResult.getRemoteAsmPath(),
                testResult.getRemoteActualOutputPath(),
                testResult.getRemoteExecutablePath());

        for (var target : targetList) {
            try {
                sftp.rm(target);
            } catch (SftpException e) {
                if (e.id == 2) continue; // File not exist
                e.printStackTrace();
                return false;
            }
        }
        return true;
    }
}
