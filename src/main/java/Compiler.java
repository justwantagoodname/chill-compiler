import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.frontend.parser.SysyLexer;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.IRGenerator;
import top.voidc.misc.AssemblyBuilder;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.logging.Logger;

public class Compiler {
    private static final Logger LOGGER = Logger.getLogger(Log.class.getName());
    public static void main(String[] args) throws IOException {
        var so = Compiler.class.getResourceAsStream("fastvm.so");
        if (so != null) {
            var br = new BufferedReader(new InputStreamReader(so));
            while (br.ready()) {
                System.out.print(br.readLine() + "\n");
            }
        }

        Flag.init(args);
        final String sourcePath = Flag.get("source");
        Log.should(sourcePath != null, "source file not specified");
        Log.d("sourcePath: " + sourcePath);

        final var input = CharStreams.fromFileName(sourcePath);
        final var lexer = new SysyLexer(input);
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser = new SysyParser(tokenStream);
        final var tree = parser.compUnit();

        IRGenerator irGen = new IRGenerator();
        final var ir = irGen.visit(tree);

        Log.should(ir != null, "ir is null");
        Log.d('\n' + ir.toString());

    }

    public static void writeFake() throws IOException {
        final String outputPath = Flag.get("-o");
        Log.should(outputPath != null, "output file not specified");
        Log.d("outputPath: " + outputPath);

        AssemblyBuilder assemblyBuilder = new AssemblyBuilder(outputPath);

        // Write the assembly code to the file
        assemblyBuilder.writeRaw(".syntax unified\n");
        assemblyBuilder.writeRaw(".arch armv7-a\n");
        assemblyBuilder.writeRaw(".fpu vfpv4\n");
        assemblyBuilder.writeRaw(".global main\n");
        assemblyBuilder.writeRaw(".text\n");
        assemblyBuilder.writeRaw(".align 2\n");
        assemblyBuilder.writeRaw(".type main, %function\n");
        assemblyBuilder.writeRaw("main:\n");
        assemblyBuilder.writeRaw("push {fp, lr}\n");
        assemblyBuilder.writeRaw("add fp, sp, #4\n");
        assemblyBuilder.writeRaw("mov r0, #0\n");
        assemblyBuilder.writeRaw("SYS_Y_0:\n");
        assemblyBuilder.writeRaw("sub sp, fp, #4\n");
        assemblyBuilder.writeRaw("pop {fp, pc}\n");
        assemblyBuilder.writeRaw("\n");
        assemblyBuilder.writeRaw(".section\t.note.GNU-stack,\"\",%progbits\n");
        assemblyBuilder.writeRaw(".ident\t\"SysY-Compiler\"\n");

        // Close the file
        assemblyBuilder.close();
    }
}