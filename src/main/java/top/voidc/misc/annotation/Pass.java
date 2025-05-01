package top.voidc.misc.annotation;

import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Pass {
    boolean enable() default true;
    boolean disable() default false;
    String[] group() default {};
}
