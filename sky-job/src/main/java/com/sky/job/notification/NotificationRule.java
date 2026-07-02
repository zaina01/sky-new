package com.sky.job.notification;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationRule(
        long id,
        String ruleId,
        String displayName,
        boolean enabled,
        NotificationEventType eventType,
        String pluginId,
        String jobId,
        String severity,
        List<String> channelIds,
        String remark,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public NotificationRule {
        channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
    }
}
