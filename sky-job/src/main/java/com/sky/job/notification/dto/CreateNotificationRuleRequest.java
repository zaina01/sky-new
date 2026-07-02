package com.sky.job.notification.dto;

import com.sky.job.notification.NotificationEventType;

import java.util.List;

public record CreateNotificationRuleRequest(
        String ruleId,
        String displayName,
        Boolean enabled,
        NotificationEventType eventType,
        String pluginId,
        String jobId,
        String severity,
        List<String> channelIds,
        String remark
) {
}
