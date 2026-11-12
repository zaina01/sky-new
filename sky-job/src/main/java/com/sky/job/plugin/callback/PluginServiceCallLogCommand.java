package com.sky.job.plugin.callback;

public record PluginServiceCallLogCommand(
        Long jobId,
        Long executionId,
        String callerInstallationId,
        String callerPluginId,
        String callerOwnerUserId,
        Long callerOwnerDepartmentId,
        String callerInstanceId,
        String callerRequestId,
        String targetInstallationId,
        String targetPluginId,
        String targetOwnerUserId,
        Long targetOwnerDepartmentId,
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
