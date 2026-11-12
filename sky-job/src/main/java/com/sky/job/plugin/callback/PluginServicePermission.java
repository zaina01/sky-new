package com.sky.job.plugin.callback;

import java.time.LocalDateTime;

public record PluginServicePermission(
        long id,
        String callerInstallationId,
        String callerPluginId,
        String targetInstallationId,
        String targetPluginId,
        String operation,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
