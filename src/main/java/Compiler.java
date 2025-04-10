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
import java.util.Objects;

public class Compiler {
    public final String sourcePath;
    public final String outputPath;
    public final File source;
    public final IceContext context = new IceContext();

    public Compiler(String sourcePath, String outputPath) {
        this.sourcePath = Objects.requireNonNull(sourcePath);
        this.outputPath = Objects.requireNonNull(outputPath);

        Log.d("sourcePath: " + sourcePath);
        Log.d("outputPath: " + outputPath);

        this.source = new File(sourcePath);
        Log.should(source.exists(), "source file does not exist");

        context.setSource(source);
    }

    public void compile() throws IOException {
        parseSource(context);

        IRGenerator generator = new IRGenerator(context);
        generator.generateIR();

        emitLLVM();

        AssemblyBuilder assemblyBuilder = new AssemblyBuilder(outputPath);
        assemblyBuilder.writeRaw(context.getCurrentIR().toString());
        assemblyBuilder.close();
    }

    public void parseSource(IceContext context) throws IOException {
        final var input = CharStreams.fromFileName(context.getSource().getAbsolutePath());
        final var lexer = new SysyLexer(input);
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser = new SysyParser(tokenStream);
        context.setAst(parser.compUnit());
        context.setParser(parser);
    }

    public void emitLLVM() throws IOException {
        if (Boolean.TRUE.equals(Flag.get("-S"))) {
            final var irPath = sourcePath.replace(".sy", ".ll");
            AssemblyBuilder assemblyBuilder = new AssemblyBuilder(irPath);
            assemblyBuilder.writeRaw(context.getCurrentIR().toString());
            assemblyBuilder.close();
        }
    }

    public static void main(String[] args) throws IOException {
        Flag.init(args);
        final String sourcePath = Flag.get("source");
        final String outputPath = Flag.get("-o");

        final var compiler = new Compiler(sourcePath, outputPath);
        compiler.compile();
    }
}