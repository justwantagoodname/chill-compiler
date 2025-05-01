package top.voidc.misc.annotation;

import top.voidc.optimizer.pass.CompilePass;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Pass {
    boolean enable() default true;
    boolean disable() default false;
}
