package top.voidc.misc.annotation;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * This annotation is used to mark anything you
 * wish to express your gratitude and worship to Sam Billon.
 * @author null
 */
@Retention(RetentionPolicy.SOURCE)
public @interface ThankAndWorshipSam {
    /**
     * The reason for thanking and worshipping the person.
     * @return the reason for thanking and worshipping
     */
    String reason() default "For being awesome!";
}
