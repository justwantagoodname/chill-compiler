package top.voidc.e2e.environment;

import java.io.*;

public class ProcessHelper {
    public record ExecuteResult(int exitCode, String stdout, String stderr) {
        public boolean isSuccess() {
            return exitCode == 0;
        }
    }

    public static String readStream(InputStream stream) throws IOException {
        BufferedReader reader = new BufferedReader(new InputStreamReader(stream));
        StringBuilder result = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            result.append(line).append("\n");
        }
        return result.toString().trim();
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

        return new ExecuteResult(exitCode, readStream(process.getInputStream()), readStream(process.getErrorStream()));
    }
}
