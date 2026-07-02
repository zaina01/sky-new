package com.sky.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ConfigField {
    String label();

    ConfigFieldType type() default ConfigFieldType.TEXT;

    boolean required() default false;

    String[] options() default {};

    Class<?> itemClass() default Void.class;

    String visibleWhenField() default "";

    String visibleWhenEquals() default "";

    String[] visibleWhenIn() default {};
}
