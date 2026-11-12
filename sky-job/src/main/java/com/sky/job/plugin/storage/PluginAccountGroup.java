package com.sky.job.plugin.storage;

import java.time.LocalDateTime;

public record PluginAccountGroup(
        long groupId,
        String name,
        long accountCount,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
