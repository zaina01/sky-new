package com.sky.job.scheduler.dto;

import com.sky.job.scheduler.JobExecution;
import com.sky.job.scheduler.JobExecutionStatus;
import com.sky.job.scheduler.JobTriggerType;

import java.time.LocalDateTime;

public record JobExecutionResponse(
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
    public static JobExecutionResponse from(JobExecution execution) {
        return new JobExecutionResponse(
                execution.id(),
                execution.jobId(),
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
                execution.createdAt()
        );
    }
}
