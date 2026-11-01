package xyz.wagyourtail.jsmacros.core.event;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;


@Retention(RetentionPolicy.RUNTIME)
public @interface Event {
    String value();
    boolean cancellable() default false;
    boolean joinable() default false;
}
