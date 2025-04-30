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

    private enum PassType {
        FUNCTION,
        UNIT
    }

    public PassManager(IceContext context) {
        this.context = context;
    }

    public void setExecutionOrder(Consumer<PassManager> executionConfig) {
        this.executionConfig = executionConfig;
    }

    public void runAll() {
        Log.should(executionConfig != null, "Execution config is not set");
        executionConfig.accept(this);
    }

    private CompilePass<?> instantiatePass(Class<? extends CompilePass<?>> clazz) {
        try {
            Constructor<? extends CompilePass<?>> passConstructor;
            if (Arrays.stream(clazz.getConstructors())
                    .anyMatch(constructor -> constructor.getParameterCount() == 1 && constructor.getParameterTypes()[0] == IceContext.class)) {
                passConstructor = clazz.getConstructor(IceContext.class);
                return passConstructor.newInstance(context);
            } else {
                passConstructor = clazz.getConstructor();
                return passConstructor.newInstance();
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate Pass: " + clazz.getName(), e);
        }
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
        return !clazz.isAnnotationPresent(Pass.class)
                || !clazz.getAnnotation(Pass.class).enable()
                || clazz.getAnnotation(Pass.class).disable();
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

        try {
            return switch (getPassRunTarget(clazz)) {
                case UNIT -> {
                    Log.should(!parallel, "Pass " + clazz.getName() + " 为 Unit 级别不支持并行");
                    @SuppressWarnings("unchecked") final var targetPass = (CompilePass<IceUnit>) instantiatePass(clazz);
                    yield targetPass.run(context.getCurrentIR());
                }
                case FUNCTION -> {
                    final var functionStream = parallel ? context.getCurrentIR().getFunctions().parallelStream()
                            : context.getCurrentIR().getFunctions().stream();
                    yield functionStream
                            .filter(function -> !(function instanceof IceExternFunction))
                            .map(function -> {
                                @SuppressWarnings("unchecked") final var targetPass = (CompilePass<IceFunction>) instantiatePass(clazz);
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
    public final void utilStable(Class<? extends CompilePass<?>>... classes) {
        boolean flag;
        do {
            flag = Arrays.stream(classes)
                    .map(this::runPass)
                    .reduce(false, (a, b) -> a || b);
        } while (flag);
    }
}
