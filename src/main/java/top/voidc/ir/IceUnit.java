package top.voidc.ir;

import top.voidc.ir.ice.constant.IceConstant;
import top.voidc.ir.ice.constant.IceFunction;

import java.util.ArrayList;
import java.util.List;

public class IceUnit extends IceValue {
    public final String name;
    public final List<IceFunction> functions;
    public final List<IceConstant> globalVariables;

    public IceUnit(String name) {
        this.name = name;
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
        final var sb = new StringBuffer();
        globalVariables.stream().forEachOrdered(v -> sb.append(v.toString()).append("\n"));
        functions.stream().forEachOrdered(f -> sb.append(f.toString()).append("\n"));
        return sb.toString();
    }
}
