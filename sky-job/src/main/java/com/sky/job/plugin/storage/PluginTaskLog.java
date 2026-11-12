package com.sky.job.plugin.storage;

import java.time.LocalDateTime;

public record PluginTaskLog(
        long id,
        String installationId,
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
}
