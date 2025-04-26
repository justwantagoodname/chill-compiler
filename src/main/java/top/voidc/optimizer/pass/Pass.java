package top.voidc.optimizer.pass;

public interface Pass <T> {
    /**
     * Apply this pass to the target.
     *
     * @param target The target to apply this pass to.
     */
    void run(T target);
}
