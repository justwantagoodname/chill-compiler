package top.voidc.ir;

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

    @Override
    public String toString() {
        final var sb = new StringBuffer();
        globalVariables.stream().forEachOrdered(v -> sb.append(v.toString()).append("\n"));
        functions.stream().forEachOrdered(f -> sb.append(f.toString()).append("\n"));
        return sb.toString();
    }
}
