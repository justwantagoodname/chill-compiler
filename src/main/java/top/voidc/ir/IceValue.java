package top.voidc.ir;

import top.voidc.ir.ice.type.IcePtrType;
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

    public Iterable<? extends IceUser> getUsers() {
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
        if (name == null) {
            return (withType ? getType() + " %" : "%") + getType() + "unnamed";
        } else {
            return (withType ? getType() + " %" : "%") + name;
        }
    }

    public String getReferenceName() {
        return getReferenceName(true);
    }

    public void getTextIR(StringBuilder builder) {
        builder.append(getReferenceName());
    }

    public void setAlign(int align) {
        this.align = align;
    }

    public int getAlign() {
        return this.align;
    }

}

