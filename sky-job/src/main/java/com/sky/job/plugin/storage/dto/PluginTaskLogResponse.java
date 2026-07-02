package com.sky.job.plugin.storage.dto;

import com.sky.job.plugin.storage.PluginTaskLog;

import java.time.LocalDateTime;

public record PluginTaskLogResponse(
        long id,
        String pluginId,
        String instanceId,
        String jobId,
        String requestId,
        String level,
        String message,
        long sequenceNo,
        LocalDateTime eventTime,
        LocalDateTime createdAt
) {
    public static PluginTaskLogResponse from(PluginTaskLog log) {
        return new PluginTaskLogResponse(
                log.id(),
                log.pluginId(),
                log.instanceId(),
                log.jobId(),
                log.requestId(),
                log.level(),
                log.message(),
                log.sequenceNo(),
                log.eventTime(),
                log.createdAt()
        );
    }
}
