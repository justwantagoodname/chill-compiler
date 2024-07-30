package top.voidc.ir;

public class IceUse {
    private final IceUser user;
    private final IceValue value;

    public IceUse(IceUser user, IceValue value) {
        this.user = user;
        this.value = value;
    }

    public IceUser getUser() {
        return user;
    }

    public IceValue getValue() {
        return value;
    }

    @Override
    public String toString() {
        return "IceUse{" +
                "user=" + user +
                ", value=" + value +
                '}';
    }
}
