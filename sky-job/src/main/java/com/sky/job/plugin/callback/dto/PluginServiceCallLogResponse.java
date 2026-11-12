package com.sky.job.plugin.callback.dto;

import com.sky.job.plugin.callback.PluginServiceCallLog;

import java.time.LocalDateTime;

public record PluginServiceCallLogResponse(
        long id,
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
        String errorMessage,
        LocalDateTime createdAt
) {
    public static PluginServiceCallLogResponse from(PluginServiceCallLog log) {
        return new PluginServiceCallLogResponse(
                log.id(),
                log.jobId(),
                log.executionId(),
                log.callerInstallationId(),
                log.callerPluginId(),
                log.callerOwnerUserId(),
                log.callerOwnerDepartmentId(),
                log.callerInstanceId(),
                log.callerRequestId(),
                log.targetInstallationId(),
                log.targetPluginId(),
                log.targetOwnerUserId(),
                log.targetOwnerDepartmentId(),
                log.targetInstanceId(),
                log.operation(),
                log.serviceRequestId(),
                log.targetRunnerLogPath(),
                log.timeoutMillis(),
                log.durationMillis(),
                log.requestBytes(),
                log.responseBytes(),
                log.success(),
                log.errorType(),
                log.errorMessage(),
                log.createdAt()
        );
    }
}
