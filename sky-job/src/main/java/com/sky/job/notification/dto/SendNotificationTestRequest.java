package com.sky.job.notification.dto;

public record SendNotificationTestRequest(
        String title,
        String content,
        String severity
) {
}
