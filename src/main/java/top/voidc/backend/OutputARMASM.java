package top.voidc.backend;

import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.misc.AssemblyBuilder;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.annotation.Qualifier;
import top.voidc.optimizer.pass.CompilePass;

import java.io.File;
import java.io.IOException;

/**
 * 输出最终的汇编代码
 */
@Pass(group = {"O0", "backend"})
public class OutputARMASM implements CompilePass<IceUnit>, IceArchitectureSpecification {
    private final String outputPath;
    private final File sourceFile;

    public OutputARMASM(
            @Qualifier("outputPath") String outputPath,
            @Qualifier("sourceFile") File sourceFile
    ) {
        this.outputPath = outputPath;
        this.sourceFile = sourceFile;
    }



    private void emitASM(IceUnit target) throws IOException {
        var assemblyBuilder = new AssemblyBuilder(outputPath);
        assemblyBuilder.writeLine("\t.arch " + getArchitecture())
                .writeLine("\t.file\t\"" + sourceFile.getAbsolutePath() + "\"")
                .writeLine();

        assemblyBuilder.writeLine("\t.text");

        for (var func : target.getFunctions()) {
            if (func instanceof IceExternFunction) continue;
            assert func instanceof IceMachineFunction;

            assemblyBuilder.writeRaw(func.getTextIR());
        }
        assemblyBuilder.close();
    }

    @Override
    public boolean run(IceUnit target) {
        if (Boolean.TRUE.equals(Flag.get("-S"))) {
            try {
                emitASM(target);
            } catch (IOException e) {
                Log.e("写入汇编时发生错误: " + e.getMessage());
            }
        }

        return false;
    }

    @Override
    public String getArchitecture() {
        return "armv8-a";
    }

    @Override
    public String getABIName() {
        return "linux-gnu-glibc";
    }

    @Override
    public int getBitSize() {
        return 64;
    }
}
