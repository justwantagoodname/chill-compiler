package top.voidc.ir;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.frontend.parser.IceLexer;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.ice.type.IceType;

import java.util.*;

public class IceValue {
    private int align = 4;
    private String name;

    protected IceType type;

    private final List<IceUser> uses; // 使用者

    public IceValue() {
        this.name = null;
        this.uses = new ArrayList<>();
        this.type = IceType.VOID;
    }

    public IceValue(String name, IceType type) {
        this.name = name;
        this.uses = new ArrayList<>();
        this.type = type;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    protected void setType(IceType type) {
        this.type = type;
    }

    public IceType getType() {
        return type;
    }

    public void addUse(IceUser user) {
        uses.add(user);
    }

    public void removeUse(IceUser user) {
        uses.remove(user);
    }

    public Iterable<? extends IceUser> getUsers() {
        return uses;
    }

    public List<IceUser> getUsersList() {
        return uses;
    }

    @Override
    public String toString() {
        return getReferenceName();
    }

    /**
     * 获取被引用时(作为操作数)的变量名
     * @return 操作数的形式
     */
    public String getReferenceName(boolean withType) {
        return (withType ? getType() + " %" : "%") + getName();
    }

    public final String getReferenceName() {
        return getReferenceName(true);
    }

    public void getTextIR(StringBuilder builder) {
        builder.append(getReferenceName());
    }

    protected static IceParser buildIRParser(String textIR) {
        var irStream = CharStreams.fromString(textIR);
        var tokenStream = new CommonTokenStream(new IceLexer(irStream));
        return new IceParser(tokenStream);
    }

    public void setAlign(int align) {
        this.align = align;
    }

    public int getAlign() {
        return this.align;
    }

}

