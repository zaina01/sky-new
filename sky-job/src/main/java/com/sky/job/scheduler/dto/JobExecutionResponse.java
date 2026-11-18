package com.sky.job.scheduler.dto;

import com.sky.job.scheduler.JobExecution;
import com.sky.job.scheduler.JobExecutionStatus;
import com.sky.job.scheduler.JobTriggerType;

import java.time.LocalDateTime;

public record JobExecutionResponse(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        JobExecutionStatus status,
        JobTriggerType triggerType,
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
    public JobExecutionResponse(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        JobExecutionStatus status,
        JobTriggerType triggerType,
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

    public JobExecutionResponse(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        JobExecutionStatus status,
        JobTriggerType triggerType,
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

    public JobExecutionResponse(
        long id,
        long jobId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String requestId,
        JobExecutionStatus status,
        JobTriggerType triggerType,
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

    public static JobExecutionResponse from(JobExecution execution) {
        return new JobExecutionResponse(
                execution.id(),
                execution.jobId(),
                execution.pluginInstallationId(),
                execution.pluginId(),
                execution.instanceId(),
                execution.requestId(),
                execution.status(),
                execution.triggerType(),
                execution.jobName(),
                execution.startedAt(),
                execution.finishedAt(),
                execution.durationMillis(),
                execution.message(),
                execution.resultJson(),
                execution.errorType(),
                execution.errorMessage(),
                execution.runnerLogPath(),
                execution.ownerUserId(),
                execution.ownerDepartmentId(),
                execution.createdAt(), execution.runtimeEngine(), execution.artifactSha256(), execution.environmentRevision(), execution.interpreterVersion(), execution.executionMode(), execution.scriptOptionsRevision()
        );
    }
}
