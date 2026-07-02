package com.sky.plugin.api;

public record PluginServiceRequest(
        String callerPluginId,
        String targetPluginId,
        String operation,
        String requestJson,
        String requestId,
        long timeoutMillis
) {
}
