package top.voidc.e2e.environment;

import top.voidc.e2e.runner.SSHARM64ASMRunner;
import top.voidc.e2e.runner.SSHIRRunner;
import top.voidc.e2e.runner.SSHRunner;

public class RunnerFactory {
    public static TestcaseRunner createRunner(String sshBaseDir, SSHRunner.SSHConfig config) {
        String runnerType = System.getProperty("chill.runner", "SSHIRRunner"); // 默认使用SSHIRRunner
        
        return switch (runnerType) {
            case "SSHIRRunner" -> new SSHIRRunner(sshBaseDir, config);
            case "SSHARM64ASMRunner" -> new SSHARM64ASMRunner(sshBaseDir, config);
            default -> throw new IllegalArgumentException("Unknown runner type: " + runnerType);
        };
    }
}
