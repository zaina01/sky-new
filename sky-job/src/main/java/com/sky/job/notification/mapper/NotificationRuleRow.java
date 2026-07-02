package com.sky.job.notification.mapper;

import java.time.LocalDateTime;

public record NotificationRuleRow(
        long id,
        String ruleId,
        String displayName,
        boolean enabled,
        String eventType,
        String pluginId,
        String jobId,
        String severity,
        String channelIdsJson,
        String remark,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
