package top.voidc.misc;

import java.io.*;

public class AssemblyBuilder {
    private BufferedWriter output;

    public AssemblyBuilder(String filename) throws IOException {
        if ("-".equals(filename)) {
            output = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
            output = new BufferedWriter(new FileWriter(filename));
        }
    }

    public AssemblyBuilder writeRaw(String str) throws IOException {
        output.write(str);
        return this;
    }

    public AssemblyBuilder writeLine() throws IOException {
        return writeLine("\n");
    }

    public AssemblyBuilder writeLine(String format, Object... args) throws IOException {
        String line = String.format(format, args);
        output.write(line);
        output.write("\n");
        return this;
    }

    public void close() throws IOException {
        output.close();
    }
}