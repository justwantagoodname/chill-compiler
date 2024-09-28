import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.frontend.parser.SysyLexer;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.IRGenerator;
import top.voidc.ir.IceContext;
import top.voidc.misc.AssemblyBuilder;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;

import java.io.File;
import java.io.IOException;
import java.util.logging.Logger;

public class Compiler {
    private static final Logger LOGGER = Logger.getLogger(Log.class.getName());
    public static void main(String[] args) throws IOException {
        Flag.init(args);
        final String sourcePath = Flag.get("source");
        Log.should(sourcePath != null, "source file not specified");
        Log.d("sourcePath: " + sourcePath);

        IceContext context = new IceContext();

        File source = new File(sourcePath);

        Log.should(source.exists(), "source file does not exist");

        context.setSource(source);

        parseSource(context);

        IRGenerator generator = new IRGenerator(context);

        generator.generateIR();

        Log.d('\n' + context.getCurrentIR().toString());

        final String outputPath = Flag.get("-o");
        Log.should(outputPath != null, "output file not specified");
        Log.d("outputPath: " + outputPath);

        AssemblyBuilder assemblyBuilder = new AssemblyBuilder(outputPath);

        assemblyBuilder.writeRaw(context.getCurrentIR().toString());
    }

    public static void parseSource(IceContext context) throws IOException {
        final var input = CharStreams.fromFileName(context.getSource().getAbsolutePath());
        final var lexer = new SysyLexer(input);
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser = new SysyParser(tokenStream);
        context.setAst(parser.compUnit());
    }

    public static void writeIR() throws IOException {
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