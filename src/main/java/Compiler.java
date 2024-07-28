import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.frontend.parser.SysyLexer;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.misc.AssemblyBuilder;
import top.voidc.misc.Flag;

import java.io.IOException;

public class Compiler {
    public static void main(String[] args) throws IOException {
        Flag.init(args);
        final String sourcePath = Flag.get("source");
        System.out.println(sourcePath);
        // open filestream from filepath
        final var input = CharStreams.fromString("hello world");
        final var lexer = new SysyLexer(input);
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser = new SysyParser(tokenStream);
        final var tree = parser.s();

        System.out.println(tree.toStringTree());
        final String outputPath = Flag.get("-o");

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