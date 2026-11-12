package com.sky.job.system.dto;

import com.sky.job.scheduler.JobExecutionStatus;

import java.time.LocalDateTime;

public record RecentJobExecutionFailureResponse(
        long id,
        long jobId,
        String jobName,
        String pluginId,
        String instanceId,
        JobExecutionStatus status,
        String errorType,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        Long durationMillis,
        String runnerLogPath
) {
}
