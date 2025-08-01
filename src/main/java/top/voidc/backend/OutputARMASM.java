package top.voidc.backend;

import top.voidc.ir.IceUnit;
import top.voidc.ir.ice.constant.*;
import top.voidc.ir.ice.interfaces.IceArchitectureSpecification;
import top.voidc.ir.ice.type.IcePtrType;
import top.voidc.ir.machine.IceMachineFunction;
import top.voidc.misc.AssemblyBuilder;
import top.voidc.misc.Flag;
import top.voidc.misc.Log;
import top.voidc.misc.Tool;
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


    private void emitGlobalSymbols(AssemblyBuilder assemblyBuilder, IceUnit target) throws IOException {
        assemblyBuilder.writeLine("\t.section\t.rodata");
        for (var global : target.getGlobalVariables()) {
            if (global instanceof IceGlobalVariable globalVariable && ((IcePtrType<?>) global.getType()).isConst()) {
                assemblyBuilder.writeLine("\t.global\t" + globalVariable.getName())
                        .writeLine("\t.type\t" + globalVariable.getName() + ", @object")
                        .writeLine(globalVariable.getName() + ":");

                assert globalVariable.getInitializer() != null;

                var arrayInitializer = (IceConstantArray) globalVariable.getInitializer();
                for (var row : arrayInitializer.getFullElements()) {
                    if (row instanceof IceConstantInt constData) {
                        assemblyBuilder.writeLine("\t.word\t" + constData.getValue());
                    }
                }
            }
        }

        assemblyBuilder.writeLine().writeLine("\t.data");

        for (var global : target.getGlobalVariables()) {
            // 跳过常量
            if (((IcePtrType<?>) global.getType()).isConst()) continue;

            if (global instanceof IceGlobalVariable globalVariable && globalVariable.getInitializer() != null
                    && globalVariable.getInitializer() instanceof IceConstantArray arrayInitializer
                    && arrayInitializer instanceof IceConstantString) continue; // 跳过字符串

            if (global instanceof IceGlobalVariable globalVariable && !((IcePtrType<?>) global.getType()).getPointTo().isArray()) {
                // 非数组类型
                assemblyBuilder.writeLine("\t.global\t" + globalVariable.getName())
                        .writeLine("\t.type\t" + globalVariable.getName() + ", @object")
                        .writeLine(globalVariable.getName() + ":");
                if (globalVariable.getInitializer() != null) {
                    switch (globalVariable.getInitializer()) {
                        case IceConstantInt constInt -> assemblyBuilder.writeLine("\t.word\t" + constInt.getValue());
                        case IceConstantFloat constant -> assemblyBuilder.writeLine("\t.word\t" + Float.floatToIntBits(constant.getValue()));
                        default -> throw new IllegalStateException("Unexpected value: " + globalVariable.getInitializer());
                    }
                } else {
                    assemblyBuilder.writeLine("\t.zero\t" + globalVariable.getType().getByteSize());
                }
            } else if (global instanceof IceGlobalVariable globalVariable && ((IcePtrType<?>) global.getType()).getPointTo().isArray()) {
                assemblyBuilder.writeLine("\t.global\t" + globalVariable.getName())
                        .writeLine("\t.type\t" + globalVariable.getName() + ", @object")
                        .writeLine(globalVariable.getName() + ":");

                if (globalVariable.getInitializer() != null
                        && globalVariable.getInitializer() instanceof IceConstantArray arrayInitializer
                        && !arrayInitializer.isFullZero()) {
                    for (var row : arrayInitializer.getFullElements()) {
                        if (row instanceof IceConstantInt constData) {
                            assemblyBuilder.writeLine("\t.word\t" + constData.getValue());
                        }
                    }
                } else {
                    // 如果没有初始化器 则分配一个零字节的空间
                    var arrayType = ((IcePtrType<?>) globalVariable.getType()).getPointTo();
                    assemblyBuilder.writeLine("\t.zero\t" + arrayType.getByteSize());
                }
            }
        }

        assemblyBuilder.writeLine().writeLine("\t.section\t.rodata.str1.8,\"aMS\",@progbits,1");

        for (var global : target.getGlobalVariables()) {
            if (global instanceof IceGlobalVariable globalVariable && globalVariable.getInitializer() instanceof IceConstantString) {
                // 字符串常量
                assemblyBuilder
                        .writeLine("\t.type\t" + globalVariable.getName() + ", @object")
                        .writeLine("\t.align 3")
                        .writeLine(globalVariable.getName() + ":");
                var stringData = Tool.toGNUASCIIFormat(((IceConstantString) globalVariable.getInitializer()).getRawByte());
                assemblyBuilder.writeLine("\t.ascii\t\"" + stringData + "\"");
            }
        }
    }

    private void emitFunctionASM(AssemblyBuilder assemblyBuilder, IceUnit target) throws IOException {
        assemblyBuilder.writeLine().writeLine("\t.text");
        for (var func : target.getFunctions()) {
            if (func instanceof IceExternFunction) continue; // 外部函数在汇编中不用声明 由连接器处理
            assert func instanceof IceMachineFunction;

            assemblyBuilder.writeRaw(func.getTextIR())
                    .writeLine();
        }
    }

    private void emitASM(IceUnit target) throws IOException {
        var assemblyBuilder = new AssemblyBuilder(outputPath);
        assemblyBuilder.writeLine("\t.arch " + getArchitecture())
                .writeLine("\t.file\t\"" + sourceFile.getAbsolutePath() + "\"")
                .writeLine("\t.section\t.note.GNU-stack,\"\",@progbits")
                .writeLine("\t.ident\t \"Chill-Compiler\"")
                .writeLine();

        emitGlobalSymbols(assemblyBuilder, target);
        emitFunctionASM(assemblyBuilder, target);

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
    public int getArchitectureBitSize() {
        return 64;
    }
}
