import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.frontend.parser.SysyLexer;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.IRGenerator;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.misc.AssemblyBuilder;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;
import top.voidc.optimizer.PassManager;
import top.voidc.optimizer.pass.function.Mem2Reg;
import top.voidc.optimizer.pass.function.ScalarReplacementOfAggregates;
import top.voidc.optimizer.pass.function.SmartChilletSimplifyCFG;
import top.voidc.optimizer.pass.function.SparseConditionalConstantPropagation;
import top.voidc.optimizer.pass.function.RenameVariable;

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
        context.setCurrentIR(new IceUnit(Flag.get("source")));
        IRGenerator generator = new IRGenerator(context);
        parseLibSource(context);
        generator.generateIR();

        parseSource(context);
        generator.generateIR();

        final var passManager = getPassManager();

        passManager.runAll();

        emitLLVM();

        AssemblyBuilder assemblyBuilder = new AssemblyBuilder(outputPath);
        assemblyBuilder.writeRaw(context.getCurrentIR().toString());
        assemblyBuilder.close();
    }

    /**
     * 设置 Pass 的执行顺序
     * @return PassManager
     */
    private PassManager getPassManager() {
        final var passManager = new PassManager(context);
        passManager.setExecutionOrder(pm -> {
            pm.runPass(RenameVariable.class);
            pm.runPass(Mem2Reg.class);
            pm.runPass(ScalarReplacementOfAggregates.class);
            pm.runPass(SmartChilletSimplifyCFG.class);
            pm.utilStable(
                    SparseConditionalConstantPropagation.class,
                    SmartChilletSimplifyCFG.class
            );
            pm.runPass(RenameVariable.class);
        });
        return passManager;
    }

    public void parseLibSource(IceContext context) throws IOException {
        final var headerStream = Compiler.class.getResourceAsStream("/lib.sy");
        Log.should(headerStream != null, "lib.sy not found");
        final var libSource = CharStreams.fromStream(headerStream);
        final var lexer = new SysyLexer(libSource);
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser = new SysyParser(tokenStream);
        context.setAst(parser.compUnit());
        context.setParser(parser);
    }

    public void parseSource(IceContext context) throws IOException {
        final var inputSource = CharStreams.fromFileName(context.getSource().getAbsolutePath());
        final var lexer = new SysyLexer(inputSource);
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
