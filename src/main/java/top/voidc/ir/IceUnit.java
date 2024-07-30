package top.voidc.ir;

import java.util.List;

public class IceUnit extends IceValue {
    public String name;
    public List<IceFunction> functions;
    public List<IceGlobalVariable> globalVariables;

    public IceUnit(String name) {
        this.name = name;
    }

    @Override
    public String toString() {
        return super.toString();
    }
}
