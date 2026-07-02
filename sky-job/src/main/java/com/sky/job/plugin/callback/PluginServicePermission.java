package com.sky.job.plugin.callback;

import java.time.LocalDateTime;

public record PluginServicePermission(
        long id,
        String callerPluginId,
        String targetPluginId,
        String operation,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
