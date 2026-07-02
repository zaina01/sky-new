package com.sky.job.scheduler;

import java.time.LocalDateTime;

public record JobExecution(
        long id,
        long jobId,
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
        LocalDateTime createdAt
) {
}
