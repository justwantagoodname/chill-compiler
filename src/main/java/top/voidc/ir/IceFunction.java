package top.voidc.ir;

import top.voidc.ir.type.IceType;

import java.util.List;

public class IceFunction extends IceConstant {
    public record Param(String name, IceType type) {}
    public List<Param> params;
    public IceType returnType;
    public List<IceBlock> blocks;

    public IceFunction(String name, IceType returnType, List<Param> params) {
        super(name);
        this.returnType = returnType;
        this.params = params;
    }

    public void addBlock(IceBlock block) {
        blocks.add(block);
    }

    public void removeBlock(IceBlock block) {
        blocks.remove(block);
    }


}
