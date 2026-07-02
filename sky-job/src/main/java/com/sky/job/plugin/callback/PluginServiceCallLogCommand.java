package com.sky.job.plugin.callback;

public record PluginServiceCallLogCommand(
        Long jobId,
        String callerPluginId,
        String callerInstanceId,
        String callerRequestId,
        String targetPluginId,
        String targetInstanceId,
        String operation,
        String serviceRequestId,
        String targetRunnerLogPath,
        long timeoutMillis,
        long durationMillis,
        int requestBytes,
        int responseBytes,
        boolean success,
        String errorType,
        String errorMessage
) {
}
