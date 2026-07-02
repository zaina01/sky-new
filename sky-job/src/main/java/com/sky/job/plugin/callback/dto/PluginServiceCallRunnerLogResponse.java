package com.sky.job.plugin.callback.dto;

public record PluginServiceCallRunnerLogResponse(
        long serviceCallLogId,
        String targetPluginId,
        String targetInstanceId,
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
