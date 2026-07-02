package com.sky.job.notification.mapper;

import java.time.LocalDateTime;

public record NotificationChannelRow(
        long id,
        String channelId,
        String channelType,
        String displayName,
        boolean enabled,
        String configJson,
        String remark,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
