package top.voidc.e2e.environment;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;

public class ProcessHelper {
    public record ExecuteResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static ExecuteResult execute(String... command) throws IOException, InterruptedException {
        final var builder = new ProcessBuilder(command);
        final var process = builder.start();

        int exitCode = process.waitFor();

        final var errorOutput = new StringBuilder();
        final var stdOutput = new StringBuilder();

        final var stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        final var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));


        String line;
        while ((line = errorReader.readLine()) != null) {
            errorOutput.append(line).append("\n");
        }

        while ((line = stdReader.readLine()) != null) {
            stdOutput.append(line).append("\n");
        }

        return new ExecuteResult(exitCode, stdOutput.toString(), errorOutput.toString());
    }

    public static ExecuteResult execute(File cwd, String... command) throws IOException, InterruptedException {
        final var builder = new ProcessBuilder(command);

        builder.directory(cwd);

        final var process = builder.start();
        int exitCode = process.waitFor();

        final var errorOutput = new StringBuilder();
        final var stdOutput = new StringBuilder();

        final var stdReader = new BufferedReader(new InputStreamReader(process.getInputStream()));
        final var errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()));


        String line;
        while ((line = errorReader.readLine()) != null) {
            errorOutput.append(line).append("\n");
        }

        while ((line = stdReader.readLine()) != null) {
            stdOutput.append(line).append("\n");
        }

        return new ExecuteResult(exitCode, stdOutput.toString(), errorOutput.toString());
    }
}
