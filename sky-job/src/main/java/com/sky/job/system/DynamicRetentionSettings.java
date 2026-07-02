package com.sky.job.system;

import org.springframework.stereotype.Component;

@Component
public class DynamicRetentionSettings {
    private final SystemSettingProvider provider;

    public DynamicRetentionSettings(SystemSettingProvider provider) {
        this.provider = provider;
    }

    public boolean enabled() {
        return provider.getBoolean(SystemSettingDefinitions.RETENTION_ENABLED);
    }

    public boolean scheduledEnabled() {
        return provider.getBoolean(SystemSettingDefinitions.RETENTION_SCHEDULED_ENABLED);
    }

    public String cron() {
        return provider.getString(SystemSettingDefinitions.RETENTION_CRON);
    }

    public boolean dryRun() {
        return provider.getBoolean(SystemSettingDefinitions.RETENTION_DRY_RUN);
    }

    public int minimumRetentionDays() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_MINIMUM_DAYS);
    }

    public int batchSize() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_BATCH_SIZE);
    }

    public int jobExecutionDays() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_JOB_EXECUTION_DAYS);
    }

    public int pluginTaskLogDays() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_PLUGIN_TASK_LOG_DAYS);
    }

    public int pluginAuditLogDays() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_PLUGIN_AUDIT_LOG_DAYS);
    }

    public int loginLogDays() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_LOGIN_LOG_DAYS);
    }

    public int systemLogDays() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_SYSTEM_LOG_DAYS);
    }

    public int notificationSendHistoryDays() {
        return provider.getInt(SystemSettingDefinitions.RETENTION_NOTIFICATION_SEND_HISTORY_DAYS);
    }
}
