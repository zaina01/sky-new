package com.sky.job.plugin.storage;

import java.time.LocalDateTime;

public record PluginAuditLog(
        Long id,
        String installationId,
        String pluginId,
        String instanceId,
        String actor,
        String action,
        String detailJson,
        LocalDateTime createdAt
) {
}
