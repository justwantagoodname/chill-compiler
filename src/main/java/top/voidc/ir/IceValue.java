package top.voidc.ir;

import top.voidc.ir.ice.type.IceType;

import java.util.*;

public class IceValue {
    private int align = 4;
    private String name;

    protected IceType type;

    private final Set<IceUser> uses; // 使用者

    private Map<String, Object> metadata = null;

    public IceValue() {
        this.name = null;
        this.uses = new HashSet<>();
        this.type = IceType.VOID;
    }

    public IceValue(String name, IceType type) {
        this.name = name;
        this.uses = new HashSet<>();
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
        if (name == null) {
            return getType().toString() + "unnamed";
        }
        return String.format("%s %%%s", getType(), name);
    }

    public void setAlign(int align) {
        this.align = align;
    }

    public int getAlign() {
        return this.align;
    }

    public void setMetadata(String key, Object metadata) {
        if (this.metadata == null) {
            this.metadata = new HashMap<>();
        }
        this.metadata.put(key, metadata);
    }

    public <T> T getMetadata(String key) {
        if (this.metadata == null) {
            return null;
        }
        return (T) this.metadata.get(key);
    }
}

