package top.voidc.ir;

import org.antlr.v4.runtime.CharStreams;
import org.antlr.v4.runtime.CommonTokenStream;
import top.voidc.frontend.parser.IceLexer;
import top.voidc.frontend.parser.IceParser;
import top.voidc.ir.ice.type.IceType;

import java.util.*;

public class IceValue {
    private String name;

    protected IceType type;

    // 使用者使用集合确保虽然某个操作数被同个指令使用多次的情况下其使用者唯一，使用者的操作数可能会重复多次且有序所以使用List
    private final Set<IceUser> users;

    public IceValue() {
        this.name = null;
        this.users = new HashSet<>();
        this.type = IceType.VOID;
    }

    public IceValue(String name, IceType type) {
        this.name = name;
        this.users = new HashSet<>();
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
        users.add(user);
    }

    public void removeUse(IceUser user) {
        users.remove(user);
    }

    /**
     * 获取使用者列表
     * @see IceValue#getUsers
     * @return 使用者列表
     */
    public List<IceUser> users() {
        return users.stream().toList();
    }

    /**
     * 获取使用者列表
     * @return 使用者列表
     */
    public List<IceUser> getUsers() {
        return users.stream().toList();
    }

    @Deprecated
    public List<IceUser> getUsersList() {
        return getUsers();
    }

    /**
     * 用 value 替换所有使用者的引用
     * 当 value 为 null 时，表示删除对此变量的引用
     * @param newValue 新的值，可为 null
     */
    public void replaceAllUsesWith(IceValue newValue) {
        // 使用 Set.copyOf(users) 是为了避免在遍历时删除元素导致 ConcurrentModificationException
        Set.copyOf(users).forEach(user -> user.replaceOperand(this, newValue));
    }

    /**
     * **销毁**当前变量，destroy的作用是维持正确的use/user引用关系以及和父节点的引用关系
     */
    public void destroy() {
        replaceAllUsesWith(null);
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

    public final String getTextIR() {
        StringBuilder builder = new StringBuilder();
        getTextIR(builder);
        return builder.toString();
    }

    protected static IceParser buildIRParser(String textIR) {
        var irStream = CharStreams.fromString(textIR);
        var tokenStream = new CommonTokenStream(new IceLexer(irStream));
        return new IceParser(tokenStream);
    }
}

