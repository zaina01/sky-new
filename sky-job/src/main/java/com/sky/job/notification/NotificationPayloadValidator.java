package com.sky.job.notification;

import com.sky.job.system.DynamicNotificationSettings;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;

@Component
public class NotificationPayloadValidator {
    private static final Pattern CHANNEL_ID_PATTERN = Pattern.compile("[A-Za-z0-9_.:-]{1,128}");
    private final DynamicNotificationSettings properties;
    private final NotificationSecretMasker masker;

    public NotificationPayloadValidator(DynamicNotificationSettings properties, NotificationSecretMasker masker) {
        this.properties = properties;
        this.masker = masker;
    }

    public String validateChannelId(String channelId) {
        if (channelId == null || !CHANNEL_ID_PATTERN.matcher(channelId).matches()) {
            throw new IllegalArgumentException("Invalid notification channelId");
        }
        return channelId;
    }

    public String validateRuleId(String ruleId) {
        if (ruleId == null || !CHANNEL_ID_PATTERN.matcher(ruleId).matches()) {
            throw new IllegalArgumentException("Invalid notification ruleId");
        }
        return ruleId;
    }

    public List<String> validateChannelIds(List<String> channelIds) {
        if (channelIds == null || channelIds.isEmpty()) {
            throw new IllegalArgumentException("Notification channelIds is required");
        }
        return channelIds.stream().map(this::validateChannelId).distinct().toList();
    }

    public List<String> validateRuleChannelIds(List<String> channelIds) {
        return validateChannelIds(channelIds);
    }

    public String normalizeOptionalPluginId(String pluginId) {
        return normalizeOptional(pluginId, 128);
    }

    public String normalizeOptionalJobId(String jobId) {
        return normalizeOptional(jobId, 64);
    }

    public String normalizeOptionalSeverity(String severity) {
        return normalizeOptional(severity, 32).toUpperCase();
    }

    public String validateTitle(String title) {
        String value = clean(title);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Notification title is required");
        }
        if (bytes(value) > properties.maxTitleBytes()) {
            throw new IllegalArgumentException("Notification title is too large");
        }
        return value;
    }

    public String validateContent(String content) {
        String value = clean(content);
        if (value.isBlank()) {
            throw new IllegalArgumentException("Notification content is required");
        }
        if (bytes(value) > properties.maxContentBytes()) {
            throw new IllegalArgumentException("Notification content is too large");
        }
        return value;
    }

    public String severity(String severity) {
        String value = clean(severity);
        return value.isBlank() ? "INFO" : truncate(value, 32);
    }

    public String preview(String content) {
        return truncate(masker.maskText(clean(content)), properties.maxPreviewChars());
    }

    public int bytes(String value) {
        return value == null ? 0 : value.getBytes(StandardCharsets.UTF_8).length;
    }

    public String sanitizeMessage(String value) {
        return truncate(masker.maskText(clean(value)), 1000);
    }

    private String normalizeOptional(String value, int maxChars) {
        return truncate(clean(value), maxChars);
    }

    private String clean(String value) {
        if (value == null) {
            return "";
        }
        return value.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
    }

    private String truncate(String value, int maxChars) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxChars ? value : value.substring(0, maxChars);
    }
}
