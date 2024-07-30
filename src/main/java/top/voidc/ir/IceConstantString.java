package top.voidc.ir;

public class IceConstantString extends IceConstantData {
    private static int stringCounter = 0;
    private String content;
    public IceConstantString(String value) {
        super("str" + stringCounter++);
        this.content = value;
    }

    public String getContent() {
        return content;
    }
}
