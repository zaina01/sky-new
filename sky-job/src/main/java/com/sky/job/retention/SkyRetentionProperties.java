package com.sky.job.retention;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "sky.retention")
public record SkyRetentionProperties(
        boolean enabled,
        boolean scheduledEnabled,
        String cron,
        boolean dryRun,
        int minimumRetentionDays,
        int batchSize,
        int jobExecutionDays,
        int pluginTaskLogDays,
        int pluginAuditLogDays,
        int loginLogDays,
        int systemLogDays,
        int notificationSendHistoryDays
) {
    public SkyRetentionProperties {
        if (cron == null || cron.isBlank()) cron = "0 0 3 * * ?";
        if (minimumRetentionDays <= 0) minimumRetentionDays = 7;
        if (batchSize <= 0) batchSize = 1000;
        if (jobExecutionDays <= 0) jobExecutionDays = 90;
        if (pluginTaskLogDays <= 0) pluginTaskLogDays = 90;
        if (pluginAuditLogDays <= 0) pluginAuditLogDays = 180;
        if (loginLogDays <= 0) loginLogDays = 180;
        if (systemLogDays <= 0) systemLogDays = 180;
        if (notificationSendHistoryDays <= 0) notificationSendHistoryDays = 180;
    }
}
