package com.sky.job.scheduler.dto;

public record JobExecutionLogResponse(
        long executionId,
        String pluginInstallationId,
        String pluginId,
        String instanceId,
        String runnerLogPath,
        boolean exists,
        String content,
        long offset,
        long nextOffset,
        int limitBytes,
        long sizeBytes,
        boolean eof,
        boolean truncated
) {
}
