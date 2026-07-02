package com.sky.job.plugin.callback;

import java.time.LocalDateTime;

public record PluginServiceCallLog(
        long id,
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
        String errorMessage,
        LocalDateTime createdAt
) {
}
