package com.sky.job.notification.dto;

import com.sky.job.notification.NotificationEventType;
import com.sky.job.notification.NotificationRule;

import java.time.LocalDateTime;
import java.util.List;

public record NotificationRuleResponse(
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
    public static NotificationRuleResponse from(NotificationRule rule) {
        return new NotificationRuleResponse(
                rule.ruleId(),
                rule.displayName(),
                rule.enabled(),
                rule.eventType(),
                rule.pluginId(),
                rule.jobId(),
                rule.severity(),
                rule.channelIds(),
                rule.remark(),
                rule.createdBy(),
                rule.updatedBy(),
                rule.createdAt(),
                rule.updatedAt()
        );
    }
}
