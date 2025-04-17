package top.voidc.optimizer;

import org.reflections.Reflections;
import top.voidc.ir.IceContext;
import top.voidc.misc.Log;
import top.voidc.misc.annotation.Pass;
import top.voidc.optimizer.pass.CompilePass;

import java.lang.reflect.Constructor;
import java.util.*;
import java.util.stream.Collectors;

/**
 * TODO: 合并了两种 Pass 的架构，这个类非常非常非常有可能需要改
 */
public class PassManager {
    private final IceContext context;
    private final Map<Class<? extends CompilePass<?>>, CompilePass<?>> passInstances = new HashMap<>();
    private final List<Class<? extends CompilePass<?>>> executionOrder = new ArrayList<>();

    public PassManager(IceContext context) {
        this.context = context;
    }

    public void scanPackage(String basePackage) {
        Set<Class<? extends CompilePass>> passClasses = findPassClasses(basePackage);
        buildExecutionOrder(passClasses);
    }

    public void runAll() {
        for (Class<? extends CompilePass> passClass : executionOrder) {
            CompilePass pass = createPassInstance(passClass);
            Log.i(">>> Running " + pass.getName());
            // TODO: 想想怎么改
//            pass.run();
            Log.i(">>> End " + pass.getName());
        }
    }

    private void buildExecutionOrder(Set<Class<? extends CompilePass>> passClasses) {
        Set<Class<? extends CompilePass>> visited = new HashSet<>();

        for (Class<? extends CompilePass> passClass : passClasses) {
            visit(passClass, visited, new HashSet<>());
        }
    }

    private void visit(Class<? extends CompilePass> clazz,
                       Set<Class<? extends CompilePass>> visited,
                       Set<Class<? extends CompilePass>> path) {
        if (visited.contains(clazz)) return;
        if (path.contains(clazz)) throw new RuntimeException("Cyclic dependency in Passes!");

        path.add(clazz);

        Pass annotation = clazz.getAnnotation(Pass.class);
        if (annotation != null) {
            for (Class<? extends CompilePass> dep : annotation.require()) {
                visit(dep, visited, path);
            }
        }

        executionOrder.add((Class<? extends CompilePass<?>>) clazz);
        visited.add(clazz);
        path.remove(clazz);
    }

    private CompilePass createPassInstance(Class<? extends CompilePass> clazz) {
        if (passInstances.containsKey(clazz)) {
            return passInstances.get(clazz);
        }
        try {
            Constructor<? extends CompilePass> ctor = clazz.getConstructor(IceContext.class);
            CompilePass pass = ctor.newInstance(context);
            passInstances.put((Class<? extends CompilePass<?>>) clazz, pass);
            return pass;
        } catch (Exception e) {
            throw new RuntimeException("Failed to instantiate Pass: " + clazz.getName(), e);
        }
    }

    private Set<Class<? extends CompilePass>> findPassClasses(String basePackage) {
        Reflections reflections = new Reflections(basePackage);
        return reflections.getTypesAnnotatedWith(Pass.class)
                .stream()
                .filter(CompilePass.class::isAssignableFrom)
                .map(c -> (Class<? extends CompilePass>) c)
                .collect(Collectors.toSet());
    }
}
