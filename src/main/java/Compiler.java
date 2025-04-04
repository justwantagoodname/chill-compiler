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

public class Compiler {
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

        final String outputPath = Flag.get("-o");
        Log.should(outputPath != null, "output file not specified");
        Log.d("outputPath: " + outputPath);

        if (Boolean.TRUE.equals(Flag.get("-S"))) {
            final var irPath = sourcePath.replace(".sy", ".ll");
            AssemblyBuilder assemblyBuilder = new AssemblyBuilder(irPath);
            assemblyBuilder.writeRaw(context.getCurrentIR().toString());
            assemblyBuilder.close();
        }

        AssemblyBuilder assemblyBuilder = new AssemblyBuilder(outputPath);

        assemblyBuilder.writeRaw(context.getCurrentIR().toString());
        assemblyBuilder.close();
    }

    public static void parseSource(IceContext context) throws IOException {
        final var input = CharStreams.fromFileName(context.getSource().getAbsolutePath());
        final var lexer = new SysyLexer(input);
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser = new SysyParser(tokenStream);
        context.setAst(parser.compUnit());
    }
}