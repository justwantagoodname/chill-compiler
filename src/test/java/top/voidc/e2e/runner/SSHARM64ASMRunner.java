package top.voidc.e2e.runner;

import com.jcraft.jsch.SftpException;
import top.voidc.e2e.environment.Testcase;
import top.voidc.misc.Log;


import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SSHARM64ASMRunner extends SSHRunner {

    public SSHARM64ASMRunner(String baseDir, SSHConfig sshConfig) {
        super(baseDir, sshConfig);
    }

    @Override
    public List<String> getCompileArgument(Testcase test) {
        return List.of(test.src().getAbsolutePath(), "-S", "-o", test.asm().getAbsolutePath());
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

        Log.d("上传汇编代码 " + test.asm().getName());

        assertTrue(test.asm().exists());

        try {
            uploadFile(test.asm(), result.getRemoteAsmPath());
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
            final var gccResult = executeCommand("gcc", "-march=armv8-a", "-o", testResult.getRemoteExecutablePath(),
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

        final var targetList = List.of(
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
