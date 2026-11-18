package com.sky.job.scheduler.mapper;

import java.time.LocalDateTime;

public record JobExecutionRow(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        String status,
        String triggerType,
        String jobName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMillis,
        String message,
        String resultJson,
        String errorType,
        String errorMessage,
        String runnerLogPath,
        String ownerUserId,
        Long ownerDepartmentId,
        LocalDateTime createdAt,
        String runtimeEngine,
        String artifactSha256,
        String environmentRevision,
        String interpreterVersion,
        String executionMode,
        String scriptOptionsRevision
) {
    public JobExecutionRow(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        String status,
        String triggerType,
        String jobName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMillis,
        String message,
        String resultJson,
        String errorType,
        String errorMessage,
        String runnerLogPath,
        String ownerUserId,
        Long ownerDepartmentId,
        LocalDateTime createdAt,
        String runtimeEngine,
        String artifactSha256,
        String environmentRevision,
        String interpreterVersion,
        String executionMode
    ) {
        this(id, jobId, pluginInstallationId, pluginId, instanceId, requestId, status, triggerType, jobName, startedAt, finishedAt, durationMillis, message, resultJson, errorType, errorMessage, runnerLogPath, ownerUserId, ownerDepartmentId, createdAt, runtimeEngine, artifactSha256, environmentRevision, interpreterVersion, executionMode, "");
    }

    public JobExecutionRow(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        String status,
        String triggerType,
        String jobName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMillis,
        String message,
        String resultJson,
        String errorType,
        String errorMessage,
        String runnerLogPath,
        String ownerUserId,
        Long ownerDepartmentId,
        LocalDateTime createdAt,
        String runtimeEngine,
        String artifactSha256,
        String environmentRevision,
        String interpreterVersion
    ) {
        this(id, jobId, pluginInstallationId, pluginId, instanceId, requestId, status, triggerType, jobName, startedAt, finishedAt, durationMillis, message, resultJson, errorType, errorMessage, runnerLogPath, ownerUserId, ownerDepartmentId, createdAt, runtimeEngine, artifactSha256, environmentRevision, interpreterVersion, "plugin");
    }

    public JobExecutionRow(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        String status,
        String triggerType,
        String jobName,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMillis,
        String message,
        String resultJson,
        String errorType,
        String errorMessage,
        String runnerLogPath,
        String ownerUserId,
        Long ownerDepartmentId,
        LocalDateTime createdAt
    ) {
        this(id, jobId, pluginInstallationId, pluginId, instanceId, requestId, status, triggerType, jobName, startedAt, finishedAt, durationMillis, message, resultJson, errorType, errorMessage, runnerLogPath, ownerUserId, ownerDepartmentId, createdAt, "java", "", "", "");
    }

}
