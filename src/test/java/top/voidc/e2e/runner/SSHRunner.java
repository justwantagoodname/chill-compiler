package top.voidc.e2e.runner;

import com.jcraft.jsch.*;
import top.voidc.e2e.environment.ProcessHelper;
import top.voidc.e2e.environment.Testcase;
import top.voidc.e2e.environment.TestcaseRunner;
import top.voidc.misc.Log;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 通过SSH在远程开发板上进行测试
 * 需要GNU GCC和GNU Make工具
 * 现阶段进行LLVM IR测试需要Clang 17以上版本
 */
public abstract class SSHRunner implements TestcaseRunner {

    protected String workingDirectory;

    public record SSHConfig(String host, int port, String username, String password) {}

    protected class TestResult {
        public Testcase getTestcase() {
            return testcase;
        }

        public String getRemoteInputPath() {
            return remoteInputPath;
        }

        public String getRemoteActualOutputPath() {
            return remoteActualOutputPath;
        }

        private final Testcase testcase;

        public String getRemoteAsmPath() {
            return remoteAsmPath;
        }

        public String getRemoteExecutablePath() {
            return remoteExecutablePath;
        }

        private final String remoteAsmPath;
        private final String remoteInputPath;
        private final String remoteExecutablePath;
        private final String remoteActualOutputPath;
        private final String remoteIrPath;

        private final File actualOutput;
        private final File irOutput;

        public TestResult(Testcase testcase) {
            this.testcase = testcase;
            this.remoteAsmPath = workingDirectory + "/" + testcase.asm().getName();
            this.remoteIrPath = workingDirectory + "/" + testcase.name() + ".ll";
            this.remoteInputPath = testcase.in() == null ? null : workingDirectory + "/" + testcase.in().getName();
            this.remoteActualOutputPath = workingDirectory + "/" + testcase.name() + ".actual.out";
            this.irOutput = new File(testcase.src().getParentFile(), testcase.name() + ".ll");
            this.actualOutput = new File(testcase.src().getParentFile(), testcase.name() + ".actual.out");
            this.remoteExecutablePath = workingDirectory + "/" + testcase.name() + ".exe";
        }

        public File getActualOutput() {
            return actualOutput;
        }

        public File getIrOutput() {
            return irOutput;
        }

        public String getRemoteIrPath() {
            return remoteIrPath;
        }
    }

    protected final Map<String, TestResult> testResultMap = new HashMap<>();

    protected final JSch jSch;
    protected final SSHConfig sshConfig;
    protected Session session;
    protected ChannelSftp sftp;

    public SSHRunner(String baseDir, SSHConfig sshConfig) {
        this.jSch = new JSch();
        this.sshConfig = sshConfig;
        workingDirectory = baseDir;
    }

    public boolean remoteFileExists(String remotePath) {
        try {
            SftpATTRS attrs = sftp.stat(remotePath);
            return attrs != null; // 文件存在
        } catch (SftpException e) {
            // 如果文件不存在会抛出异常
            if (e.id == ChannelSftp.SSH_FX_NO_SUCH_FILE) {
                return false;
            } else {
                throw new RuntimeException("Error checking remote file: " + e.getMessage(), e);
            }
        }
    }

    protected void uploadFile(File local, String remotePath) throws SftpException {
        sftp.put(local.getAbsolutePath(), remotePath);
    }

    /**
     * 使用 SFTP 从远程服务器下载文件到本地指定位置。
     *
     * @param remotePath    远程文件路径（例如 "/home/pi/output.txt"）
     * @param localFile     本地目标文件（可以指定具体路径和文件名）
     * @throws SftpException 如果下载过程中发生错误
     */
    protected void downloadFile(String remotePath, File localFile)
            throws SftpException {
        sftp.get(remotePath, localFile.getAbsolutePath());
    }

    /**
     * 上传指定本地目录中的所有一级文件（不包括子目录和递归）到远程服务器指定路径。
     *
     * @param localDirPath   本地目录路径，例如 "/home/user/build"
     * @param remoteDirPath  远程目录路径，例如 "/home/pi/build"
     * @throws SftpException 如果文件传输过程中出现错误
     */
    protected void uploadDirectoryFlat(String localDirPath, String remoteDirPath)
            throws SftpException {

        File localDir = new File(localDirPath);
        if (!localDir.exists() || !localDir.isDirectory()) {
            throw new IllegalArgumentException("本地路径不是有效目录: " + localDirPath);
        }

        // 尝试进入远程目录
        try {
            sftp.cd(remoteDirPath);
        } catch (SftpException e) {
            sftp.mkdir(remoteDirPath);
        }

        // 遍历目录中所有一级文件
        for (File file : Objects.requireNonNull(localDir.listFiles())) {
            final var dstFile = remoteDirPath + '/' + file.getName();
            if (file.isFile()) {
                uploadFile(file, dstFile);
                Log.i("上传文件: " + dstFile);
            }
        }
    }

    protected ProcessHelper.ExecuteResult executeCommand(String command) throws JSchException, IOException {
        final var channel = (ChannelExec) session.openChannel("exec");

        Log.d("执行 " + command);
        channel.setCommand(command);
        final var stdout = channel.getInputStream();
        final var stderr = channel.getErrStream();

        channel.connect();

        String output = ProcessHelper.readStream(stdout);
        String error = ProcessHelper.readStream(stderr);

        // https://stackoverflow.com/questions/22734134/wait-for-jsch-command-to-execute-instead-of-hard-coding-a-fixed-time-to-wait-for
        while (!channel.isClosed()) {
            try {
                Thread.sleep(100);
            } catch (InterruptedException ignored) {}
        }

        int exitStatus = channel.getExitStatus();
        channel.disconnect();

        return new ProcessHelper.ExecuteResult(exitStatus, output, error);
    }

    protected ProcessHelper.ExecuteResult executeCommand(String... command) throws JSchException, IOException {
        return executeCommand(String.join(" ", command));
    }

    protected void compileLibsysy() throws IOException, JSchException, SftpException {


        final var libsysyDir = new File("testcases/libsysy");
        final var remoteLibsysyDir = workingDirectory + "/libsysy";
        final var remoteLibsysy = remoteLibsysyDir + "/libsysy.a";

        if (remoteFileExists(remoteLibsysy)) {
            return;
        }

        Log.i("Compiling Libsysy");

        uploadDirectoryFlat(libsysyDir.getAbsolutePath(), remoteLibsysyDir);

        final var buildResult = executeCommand("cd " + remoteLibsysyDir + " && make -f Makefile build");

        if (!buildResult.isSuccess()) {
            Log.e("Make Output:\n===\n" + buildResult.stdout() + "===\n===\n"+ buildResult.stderr());
            throw new RuntimeException("Libsysy compilation failed: make exit code = " + buildResult.exitCode());
        }

        assertTrue(remoteFileExists(remoteLibsysy), "Libsysy compilation failed, libsysy.a not found");

        Log.i("Libsysy compiled successfully: " + remoteLibsysy);
    }

    protected static void compareOutput(TestResult result) throws IOException {
        final var expectedLines = Files.readString(result.getTestcase().out().toPath())
                .replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
        final var actualLines = Files.readString(result.getActualOutput().toPath())
                .replace("\r\n", "\n").replace("\r", "\n").stripTrailing();
        assertEquals(expectedLines, actualLines,
                "输出和预期不符合: " + result.getTestcase().name());
    }

    private void connect() throws JSchException {

        if (sshConfig.password() == null) { // 使用默认的 SSH 私钥路径
            String privateKeyPath;
            if (System.getenv("SSH_PRIVATE_KEY") != null) {
                privateKeyPath = System.getenv("SSH_PRIVATE_KEY");
            } else {
                privateKeyPath = System.getProperty("user.home") + "/.ssh/id_rsa";
            }
            jSch.addIdentity(privateKeyPath);
        }

        session = jSch.getSession(sshConfig.username(), sshConfig.host(), sshConfig.port());

        if (sshConfig.password() != null) {
            session.setPassword(sshConfig.password());
        }

        // 忽略主机指纹检查（安全部署时应使用 known_hosts）
        session.setConfig("StrictHostKeyChecking", "no");

        session.connect();

        this.sftp = (ChannelSftp) session.openChannel("sftp");
        this.sftp.connect();
    }

    private void disconnect() {
        if (session != null && session.isConnected()) {
            sftp.disconnect();
            session.disconnect();
        }
    }

    @Override
    public String getName() {
        return "SSHGNURunner";
    }

    @Override
    public String getAssemblerName() {
        return "clang";
    }

    @Override
    public boolean beforeAll() {
        Log.i("链接SSH链接中");
        try {
            connect();
        } catch (JSchException e) {
            e.printStackTrace();
            return false;
        }
        Log.i("链接已建立 " + session.getServerVersion());
        try {
            compileLibsysy();
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }

    @Override
    public boolean afterAll() {
        disconnect();
        return true;
    }

    @Override
    public boolean runTest(Testcase test) {
        final var testResult = testResultMap.get(test.name());
        final var redirectInput = testResult.getRemoteInputPath() == null ? "" : "< " + testResult.getRemoteInputPath();
        var timeOut = System.getProperty("chill.timeout") == null ? "5s" : System.getProperty("chill.timeout");
        try {
            final var executeResult = executeCommand("timeout", timeOut, testResult.getRemoteExecutablePath(), redirectInput,
                    ">", testResult.getRemoteActualOutputPath());

            Log.i("执行信息：" + executeResult.stderr());

            Log.i("获取执行结果 " + testResult.getRemoteActualOutputPath());
            if (remoteFileExists(testResult.getRemoteActualOutputPath())) {
                downloadFile(testResult.getRemoteActualOutputPath(), testResult.getActualOutput());
            } else {
                final var createRes = testResult.getActualOutput().createNewFile();
                assertTrue(createRes);
            }

            ProcessHelper.appendCode(executeResult.exitCode(), testResult.getActualOutput());
            compareOutput(testResult);

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }
}
