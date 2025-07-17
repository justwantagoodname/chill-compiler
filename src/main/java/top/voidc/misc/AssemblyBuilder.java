package top.voidc.misc;

import java.io.*;

public class AssemblyBuilder {
    private final BufferedWriter output;

    public AssemblyBuilder(String filename) throws IOException {
        if ("-".equals(filename)) {
            output = new BufferedWriter(new OutputStreamWriter(System.out));
        } else {
            output = new BufferedWriter(new FileWriter(filename));
        }
    }

    public AssemblyBuilder(BufferedWriter output) {
        this.output = output;
    }

    public AssemblyBuilder writeRaw(String str) throws IOException {
        output.write(str);
        return this;
    }

    public AssemblyBuilder writeLine(String str) throws IOException {
        output.write(str);
        output.write("\n");
        return this;
    }

    public AssemblyBuilder writeLine() throws IOException {
        output.write("\n");
        return this;
    }

    public void close() throws IOException {
        output.close();
    }
}