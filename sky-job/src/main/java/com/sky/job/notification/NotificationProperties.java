package com.sky.job.notification;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.List;

@ConfigurationProperties(prefix = "sky.notification")
public record NotificationProperties(
        List<String> alertChannelIds,
        int maxTitleBytes,
        int maxContentBytes,
        int maxPreviewChars,
        Duration httpTimeout,
        boolean retryEnabled,
        String retryCron,
        int retryBatchSize,
        int retryMaxAttempts,
        Duration retryInitialDelay,
        Duration retryMaxDelay,
        Duration retryStaleTimeout
) {
    public NotificationProperties {
        alertChannelIds = alertChannelIds == null ? List.of() : alertChannelIds.stream().filter(value -> value != null && !value.isBlank()).map(String::trim).toList();
        if (maxTitleBytes <= 0) maxTitleBytes = 512;
        if (maxContentBytes <= 0) maxContentBytes = 8192;
        if (maxPreviewChars <= 0) maxPreviewChars = 500;
        if (httpTimeout == null || httpTimeout.isNegative() || httpTimeout.isZero()) httpTimeout = Duration.ofSeconds(10);
        if (retryCron == null || retryCron.isBlank()) retryCron = "0/30 * * * * ?";
        if (retryBatchSize <= 0) retryBatchSize = 20;
        if (retryMaxAttempts <= 0) retryMaxAttempts = 3;
        if (retryInitialDelay == null || retryInitialDelay.isNegative() || retryInitialDelay.isZero()) retryInitialDelay = Duration.ofSeconds(30);
        if (retryMaxDelay == null || retryMaxDelay.isNegative() || retryMaxDelay.isZero()) retryMaxDelay = Duration.ofMinutes(10);
        if (retryStaleTimeout == null || retryStaleTimeout.isNegative() || retryStaleTimeout.isZero()) retryStaleTimeout = Duration.ofMinutes(5);
    }
}
