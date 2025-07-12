package top.voidc.backend;

import top.voidc.ir.IceUnit;
import top.voidc.misc.AssemblyBuilder;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.misc.annotation.Qualifier;
import top.voidc.optimizer.pass.CompilePass;

import java.io.IOException;

@Pass(group = {"O0"})
public class DumpIR implements CompilePass<IceUnit> {

    private final String sourcePath;
    public DumpIR(@Qualifier("sourcePath") String sourcePath) {
        this.sourcePath = sourcePath;

    }

    private void emitIR(IceUnit target) throws IOException {
        if (Boolean.TRUE.equals(Flag.get("-emit-ir"))) {
            final var irPath = sourcePath.replace(".sy", ".ll");
            var assemblyBuilder = new AssemblyBuilder(irPath);
            assemblyBuilder.writeRaw(target.getTextIR());
            assemblyBuilder.close();
        }
    }

    @Override
    public boolean run(IceUnit target) {
        try {
            emitIR(target); // 输出 IR 代码
        } catch (IOException e) {
            Log.e("写入 IR 时发生错误: " + e.getMessage());
        }
        return false;
    }
}
