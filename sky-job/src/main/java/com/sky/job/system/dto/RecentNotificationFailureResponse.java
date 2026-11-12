package com.sky.job.system.dto;

import com.sky.job.notification.NotificationChannelType;
import com.sky.job.notification.NotificationSourceType;

import java.time.LocalDateTime;

public record RecentNotificationFailureResponse(
        long id,
        String requestId,
        NotificationSourceType sourceType,
        String pluginId,
        String instanceId,
        String jobId,
        Long executionId,
        String channelId,
        NotificationChannelType channelType,
        String title,
        String errorType,
        String errorMessage,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
}
