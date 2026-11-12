package com.sky.job.notification.mapper;

import java.time.LocalDateTime;

public record NotificationRuleRow(
        long id,
        String ruleId,
        String displayName,
        boolean enabled,
        String eventType,
        String pluginInstallationId,
        String pluginId,
        String jobId,
        String severity,
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
