package com.sky.plugin.api;

public enum PluginRuntimeType {
    JAVA_PROCESS("java-process");

    private final String value;

    PluginRuntimeType(String value) {
        this.value = value;
    }

    public String value() {
        return value;
    }
}
