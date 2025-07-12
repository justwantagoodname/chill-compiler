import org.antlr.v4.runtime.CharStream;
import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.backend.*;
import top.voidc.backend.arm64.instr.pattern.ARM64InstructionPatternPack;
import top.voidc.backend.instr.InstructionSelectionPass;
import top.voidc.backend.instr.SSADestruction;
import top.voidc.frontend.parser.SysyLexer;
import top.voidc.frontend.parser.SysyParser;
import top.voidc.frontend.translator.IRGenerator;
import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;
import top.voidc.optimizer.PassManager;
import top.voidc.optimizer.pass.function.*;
import top.voidc.optimizer.pass.unit.ShowIR;

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
        context.addPassResult("sourceFile", source);
    }

    public void compile() throws IOException {
        final var passManager = getPassManager();
        this.compile(passManager);
    }

    public void compile(PassManager passManager) throws IOException {
        context.setCurrentIR(new IceUnit(Flag.get("source")));
        context.addPassResult("sourcePath", sourcePath);
        context.addPassResult("outputPath", outputPath);

        IRGenerator generator = new IRGenerator(context);
        parseLibSource(context);
        generator.generateIR();

        parseSource(context);
        generator.generateIR();

        // 编译到ARM指令
        context.addPassResult(new ARM64InstructionPatternPack());

        // TODO: 后续添加O0 O1的组
        passManager.addDisableGroup("needfix");
        // 在完成前先禁用后端相关的Pass
//        passManager.addDisableGroup("backend");
        passManager.runAll();
    }

    /**
     * 设置 Pass 的执行顺序
     * @return PassManager
     */
    private PassManager getPassManager() {
        final var passManager = new PassManager(context);
        passManager.setPipeline(pm -> {
            pm.runPass(RenameVariable.class);
            pm.runPass(ScalarReplacementOfAggregates.class);
            pm.runPass(Mem2Reg.class);
            pm.runPass(SmartChilletSimplifyCFG.class);
            pm.untilStable(
                    GlobalValueNumbering.class,
                    SparseConditionalConstantPropagation.class,
                    SmartChilletSimplifyCFG.class
            );
            pm.runPass(RenameVariable.class);
            pm.runPass(DumpIR.class);

            // 后端相关
            pm.runPass(SSADestruction.class);
            pm.runPass(InstructionSelectionPass.class);
            pm.runPass(LivenessAnalysis.class);
            pm.runPass(ShowIR.class);
            pm.runPass(SillyChilletAllocateRegister.class);
            pm.runPass(AlignFramePass.class);

            pm.runPass(OutputARMASM.class);
        });
        return passManager;
    }

    public void parseLibSource(IceContext context) throws IOException {
        final var headerStream = Compiler.class.getResourceAsStream("/lib.sy");
        Log.should(headerStream != null, "lib.sy not found");
        final var libSource = CharStreams.fromStream(headerStream);
        initParser(libSource);
    }

    public void parseSource(IceContext context) throws IOException {
        final var inputSource = CharStreams.fromFileName(context.getSource().getAbsolutePath());
        initParser(inputSource);
    }

    public void initParser(CharStream inputSource){
        final var lexer = new SysyLexer(inputSource);
        final var tokenStream = new CommonTokenStream(lexer);
        final var parser = new SysyParser(tokenStream);
        context.setAst(parser.compUnit());
        context.setParser(parser);
    }

    public static void main(String[] args) throws IOException {
        Flag.init(args);
        final String sourcePath = Flag.get("source");
        final String outputPath = Flag.get("-o");

        final var compiler = new Compiler(sourcePath, outputPath);
        compiler.compile();
    }
}
