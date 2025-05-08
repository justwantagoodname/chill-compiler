package top.voidc.optimizer;

import top.voidc.ir.IceContext;
import top.voidc.ir.IceUnit;
import top.voidc.ir.IceValue;
import top.voidc.ir.ice.constant.IceExternFunction;
import top.voidc.ir.ice.constant.IceFunction;

import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.function.Consumer;

/**
 * TODO: 合并了两种 Pass 的架构，这个类非常非常非常有可能需要改
 */
public class PassManager {
    private final IceContext context;

    private Consumer<PassManager> executionConfig = null;

    private final Set<String> disabledGroup = new HashSet<>();

    private enum PassType {
        FUNCTION,
        UNIT
    }

    public PassManager(IceContext context) {
        this.context = context;
    }

    public Set<String> getDisabledGroup() {
        return disabledGroup;
    }

    /**
     * 添加禁用的 Pass 组
     * @param group 禁用的组名
     */
    public void addDisableGroup(String group) {
        disabledGroup.add(group);
    }

    public void setPipeline(Consumer<PassManager> executionConfig) {
        this.executionConfig = executionConfig;
    }

    public void runAll() {
        Log.should(executionConfig != null, "Execution config is not set");
        executionConfig.accept(this);
    }

    private CompilePass<?> instantiatePass(Class<? extends CompilePass<?>> clazz) {
        Constructor<?>[] constructors = clazz.getConstructors(); // 只获取 public 构造器

        for (Constructor<?> constructor : constructors) {
            var paramTypes = constructor.getParameterTypes();
            List<Object> args = new ArrayList<>();

            boolean allMatched = true;
            for (Class<?> paramType : paramTypes) {
                Optional<Object> matched = context.getPassResults().stream()
                        .filter(obj -> paramType.isAssignableFrom(obj.getClass()))
                        .findFirst();

                if (matched.isPresent()) {
                    Object value = matched.get();
                    args.add(value);
                } else if (paramType.isAssignableFrom(IceContext.class)) {
                    args.add(context);
                } else {
                    allMatched = false;
                    break;
                }
            }

            if (allMatched) {
                try {
                    return clazz.cast(constructor.newInstance(args.toArray()));
                } catch (Exception e) {
                    throw new RuntimeException("Constructor invocation failed", e);
                }
            }
        }
        throw new IllegalArgumentException("创建Pass " + clazz.getSimpleName() + " 时失败");
    }

    /**
     * 获取 Pass 的运行目标
     *
     * @param clazz Pass 的 Class 对象
     * @return Pass 的运行目标
     */
    private PassType getPassRunTarget(Class<? extends CompilePass<?>> clazz) {
        final var runMethod = Arrays.stream(clazz.getMethods())
                .filter(method -> method.getName().equals("run") && !method.isSynthetic() && !method.isBridge())
                .findFirst().orElseThrow();

        final var parameterTypes = runMethod.getParameterTypes();
        Log.should(parameterTypes.length == 1, "Pass " + clazz.getName() + " run method should have one parameter");
        Log.should(IceValue.class.isAssignableFrom(parameterTypes[0]),
                "Pass " + clazz.getName() + " run method parameter should be IceValue");
        final var parameterType = parameterTypes[0];
        if (parameterType.equals(IceFunction.class)) {
            return PassType.FUNCTION;
        } else if (parameterType.equals(IceUnit.class)) {
            return PassType.UNIT;
        } else {
            throw new IllegalArgumentException("Pass " + clazz.getName() + " 的目标类型" + parameterType + "不支持");
        }
    }

    private boolean isPassDisabled(Class<? extends CompilePass<?>> clazz) {
        if (!clazz.isAnnotationPresent(Pass.class)) return true;
        if (!clazz.getAnnotation(Pass.class).enable() || clazz.getAnnotation(Pass.class).disable()) {
            return true;
        }
        for (String group : clazz.getAnnotation(Pass.class).group()) {
            if (disabledGroup.contains(group)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 运行给定的 Pass，默认非并行
     *
     * @param clazz Pass 的 Class 对象
     * @return 运行结果
     */
    public boolean runPass(Class<? extends CompilePass<?>> clazz) {
        return runPass(clazz, false);
    }

    /**
     * 运行给定的 Pass
     * 其内部使用反射来实例化 Pass 对象并决定运行目标
     *
     * @param clazz    Pass 的 Class 对象
     * @param parallel 是否并行运行
     * @return 运行结果同run方法
     */
    public boolean runPass(Class<? extends CompilePass<?>> clazz, boolean parallel) {

        if (isPassDisabled(clazz)) {
            Log.i("Pass " + clazz.getSimpleName() + " 已禁用，跳过");
            return false;
        }

        Log.i("执行Pass: " + clazz.getSimpleName());

        final var pass = instantiatePass(clazz);

        try {
            return switch (getPassRunTarget(clazz)) {
                case UNIT -> {
                    Log.should(!parallel, "Pass " + clazz.getName() + " 为 Unit 级别不支持并行");
                    @SuppressWarnings("unchecked") final var targetPass = (CompilePass<IceUnit>) pass;
                    yield targetPass.run(context.getCurrentIR());
                }
                case FUNCTION -> {
                    final var functionStream = parallel ? context.getCurrentIR().getFunctions().parallelStream()
                            : context.getCurrentIR().getFunctions().stream();
                    yield functionStream
                            .filter(function -> !(function instanceof IceExternFunction))
                            .map(function -> {
                                @SuppressWarnings("unchecked") final var targetPass = (CompilePass<IceFunction>) pass;
                                return targetPass.run(function);
                            }).reduce(false, (a, b) -> {
                                // Note：必须要使用 reduce 来合并结果，anyMatch 和 allMatch 都会短路
                                return a || b;
                            });
                }
            };
        } catch (Exception e) {
            throw new RuntimeException("运行 Pass " + clazz.getSimpleName() + " 出现错误", e);
        }
    }

    /**
     * 工具函数，运行给定的 Pass，直到IR不发生变化
     *
     * @param classes Pass 的 Class 对象可传入多个
     */
    @SafeVarargs
    public final void untilStable(Class<? extends CompilePass<?>>... classes) {
        boolean flag;
        do {
            flag = Arrays.stream(classes)
                    .map(this::runPass)
                    .reduce(false, (a, b) -> a || b);
        } while (flag);
    }
}
