package com.sky.plugin.api;

public record PluginServiceResponse(
        boolean success,
        String resultJson,
        String message,
        String errorType,
        String errorMessage
) {
    public static PluginServiceResponse success(String resultJson) {
        return new PluginServiceResponse(true, resultJson == null ? "" : resultJson, "", "", "");
    }

    public static PluginServiceResponse success(String resultJson, String message) {
        return new PluginServiceResponse(true, resultJson == null ? "" : resultJson, message == null ? "" : message, "", "");
    }

    public static PluginServiceResponse failed(String message) {
        return new PluginServiceResponse(false, "", message == null ? "" : message, "", "");
    }

    public static PluginServiceResponse failed(String message, String errorType, String errorMessage) {
        return new PluginServiceResponse(false, "", message == null ? "" : message, errorType == null ? "" : errorType, errorMessage == null ? "" : errorMessage);
    }
}
