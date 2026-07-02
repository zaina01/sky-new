package com.sky.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface SkyPlugin {
    String pluginId();

    String version();

    String displayName() default "";

    String description() default "";

    PluginRuntimeType runtime() default PluginRuntimeType.JAVA_PROCESS;

    PluginCapability[] capabilities() default {PluginCapability.TASK};

    Class<?> configClass() default Void.class;

    Class<?> accountClass() default Void.class;
}
