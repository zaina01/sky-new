package com.sky.job.notification.dto;

import com.sky.job.notification.NotificationEventType;

import java.util.List;

public record UpdateNotificationRuleRequest(
        String displayName,
        Boolean enabled,
        NotificationEventType eventType,
        String pluginInstallationId,
        String jobId,
        String severity,
        List<String> channelIds,
        String remark
) {
}
