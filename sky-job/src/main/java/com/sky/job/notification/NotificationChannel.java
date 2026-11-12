package com.sky.job.notification;

import java.time.LocalDateTime;

public record NotificationChannel(
        long id,
        String channelId,
        NotificationChannelType channelType,
        String displayName,
        boolean enabled,
        String configJson,
        String remark,
        String ownerUserId,
        String ownerUsername,
        Long ownerDepartmentId,
        String ownerDepartmentName,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
