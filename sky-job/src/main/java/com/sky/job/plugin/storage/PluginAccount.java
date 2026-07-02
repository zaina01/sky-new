package com.sky.job.plugin.storage;

import java.time.LocalDateTime;

public record PluginAccount(
        long id,
        String pluginId,
        String accountId,
        String displayName,
        String accountJson,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
