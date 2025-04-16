package top.voidc.ir;

import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceConstantData;
import top.voidc.ir.ice.constant.IceFunction;
import top.voidc.ir.ice.type.IceType;

import java.util.ArrayList;
import java.util.List;

public class IceUnit extends IceValue {
    public final List<IceFunction> functions;
    public final List<IceConstant> globalVariables;

    public IceUnit(String name) {
        super(name, IceType.VOID);
        this.globalVariables = new ArrayList<>();
        this.functions = new ArrayList<>();
    }

    public void addGlobalDecl(IceConstant decl) {
        if (globalVariables.stream().anyMatch(v -> v.getName().equals(decl.getName()))) {
            throw new RuntimeException("Duplicate global variable declaration: " + decl.getName());
        }
        globalVariables.add(decl);
    }

    public void addFunction(IceFunction function) {
        if (functions.stream().anyMatch(f -> f.getName().equals(function.getName()))) {
            throw new RuntimeException("Duplicate function declaration: " + function.getName());
        }
        functions.add(function);
    }

    @Override
    public String toString() {
        final var builder = new StringBuilder();
        getTextIR(builder);
        return builder.toString();
    }

    @Override
    public void getTextIR(StringBuilder builder) {
        builder.append("; ").append(this.getName()).append('\n');

        globalVariables.forEach(v -> {v.getTextIR(builder); builder.append('\n');});
        functions.forEach(f -> {f.getTextIR(builder); builder.append('\n');});
    }
}
