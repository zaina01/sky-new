package com.sky.job.system;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

@Component
public class DynamicNotificationSettings {
    private final SystemSettingProvider provider;

    public DynamicNotificationSettings(SystemSettingProvider provider) {
        this.provider = provider;
    }

    public List<String> alertChannelIds() {
        return provider.getStringList(SystemSettingDefinitions.NOTIFICATION_ALERT_CHANNEL_IDS);
    }

    public int maxTitleBytes() {
        return provider.getInt(SystemSettingDefinitions.NOTIFICATION_MAX_TITLE_BYTES);
    }

    public int maxContentBytes() {
        return provider.getInt(SystemSettingDefinitions.NOTIFICATION_MAX_CONTENT_BYTES);
    }

    public int maxPreviewChars() {
        return provider.getInt(SystemSettingDefinitions.NOTIFICATION_MAX_PREVIEW_CHARS);
    }

    public Duration httpTimeout() {
        return provider.getDuration(SystemSettingDefinitions.NOTIFICATION_HTTP_TIMEOUT);
    }

    public boolean retryEnabled() {
        return provider.getBoolean(SystemSettingDefinitions.NOTIFICATION_RETRY_ENABLED);
    }

    public String retryCron() {
        return provider.getString(SystemSettingDefinitions.NOTIFICATION_RETRY_CRON);
    }

    public int retryBatchSize() {
        return provider.getInt(SystemSettingDefinitions.NOTIFICATION_RETRY_BATCH_SIZE);
    }

    public int retryMaxAttempts() {
        return provider.getInt(SystemSettingDefinitions.NOTIFICATION_RETRY_MAX_ATTEMPTS);
    }

    public Duration retryInitialDelay() {
        return provider.getDuration(SystemSettingDefinitions.NOTIFICATION_RETRY_INITIAL_DELAY);
    }

    public Duration retryMaxDelay() {
        return provider.getDuration(SystemSettingDefinitions.NOTIFICATION_RETRY_MAX_DELAY);
    }

    public Duration retryStaleTimeout() {
        return provider.getDuration(SystemSettingDefinitions.NOTIFICATION_RETRY_STALE_TIMEOUT);
    }
}
