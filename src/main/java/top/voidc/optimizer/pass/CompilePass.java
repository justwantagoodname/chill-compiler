package top.voidc.optimizer.pass;

public interface CompilePass <T> {
    String getName();

    void run(T target);
}
