package top.voidc.e2e.environment;

import top.voidc.misc.Log;

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
        Log.d("执行: " + String.join(" ", command));
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

}
