package com.sky.plugin.api;

public record TaskResult(boolean succeeded, String message, String resultJson) {
    public static TaskResult success() {
        return new TaskResult(true, "", "");
    }

    public static TaskResult success(String message) {
        return new TaskResult(true, message, "");
    }

    public static TaskResult success(String message, String resultJson) {
        return new TaskResult(true, message, resultJson);
    }

    public static TaskResult failure(String message) {
        return new TaskResult(false, message, "");
    }
}
