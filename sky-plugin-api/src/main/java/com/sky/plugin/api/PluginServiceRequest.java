package com.sky.plugin.api;

public record PluginServiceRequest(
        String callerPluginId,
        String targetPluginId,
        String operation,
        String requestJson,
        String requestId,
        long timeoutMillis,
        String callerInstallationId,
        String targetInstallationId
) {
    public PluginServiceRequest(
            String callerPluginId,
            String targetPluginId,
            String operation,
            String requestJson,
            String requestId,
            long timeoutMillis
    ) {
        this(callerPluginId, targetPluginId, operation, requestJson, requestId, timeoutMillis, "", "");
    }
}
