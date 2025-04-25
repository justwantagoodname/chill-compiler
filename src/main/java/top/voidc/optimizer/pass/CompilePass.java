package top.voidc.optimizer.pass;

/**
 * Chillet IR 的优化 pass 接口
 * 任何 pass 都需要实现这个接口，并在 class 上添加 @Pass 注解
 *
 * @param <T> pass 的目标类型，可以是 IceFunction、IceUnit
 */
public interface CompilePass <T> {
    String getName();

    /**
     * 静态运行 pass
     *
     * @param target 目标优化对象
     * @return true 如果 pass 修改了目标，否则 false
     */
    boolean run(T target);
}
