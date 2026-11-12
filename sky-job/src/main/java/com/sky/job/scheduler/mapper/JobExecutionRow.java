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
        LocalDateTime createdAt
) {
}
