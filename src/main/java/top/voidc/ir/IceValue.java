package top.voidc.ir;

import top.voidc.ir.type.IceType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class IceValue {
    private int align = 4;
    private String name;

    protected IceType type;

    private final List<IceUse> uses;

    private Map<String, Object> metadata = null;

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
        if (uses.stream()
                .noneMatch((use) -> use.getUser() == user)) {
            uses.add(new IceUse(user, this));
        }
    }

    public List<IceUse> getUses() {
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

