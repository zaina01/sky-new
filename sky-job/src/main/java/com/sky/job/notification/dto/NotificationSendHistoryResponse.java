package com.sky.job.notification.dto;

import com.sky.job.notification.NotificationChannelType;
import com.sky.job.notification.NotificationSendHistory;
import com.sky.job.notification.NotificationSendStatus;
import com.sky.job.notification.NotificationSourceType;

import java.time.LocalDateTime;

public record NotificationSendHistoryResponse(
        long id,
        String requestId,
        NotificationSourceType sourceType,
        String installationId,
        String pluginId,
        String instanceId,
        String jobId,
        Long executionId,
        String sourceOwnerUserId,
        Long sourceOwnerDepartmentId,
        String channelOwnerUserId,
        Long channelOwnerDepartmentId,
        String channelId,
        NotificationChannelType channelType,
        String title,
        String contentPreview,
        int payloadBytes,
        NotificationSendStatus status,
        int attemptCount,
        int maxAttempts,
        LocalDateTime nextRetryAt,
        LocalDateTime firstAttemptAt,
        LocalDateTime lastAttemptAt,
        int manualRetryCount,
        LocalDateTime lastManualRetryAt,
        String lastManualRetryBy,
        String errorType,
        String errorMessage,
        String providerStatusCode,
        String providerMessage,
        LocalDateTime createdAt,
        LocalDateTime sentAt
) {
    public static NotificationSendHistoryResponse from(NotificationSendHistory history) {
        return new NotificationSendHistoryResponse(
                history.id(),
                history.requestId(),
                history.sourceType(),
                history.installationId(),
                history.pluginId(),
                history.instanceId(),
                history.jobId(),
                history.executionId(),
                history.sourceOwnerUserId(),
                history.sourceOwnerDepartmentId(),
                history.channelOwnerUserId(),
                history.channelOwnerDepartmentId(),
                history.channelId(),
                history.channelType(),
                history.title(),
                history.contentPreview(),
                history.payloadBytes(),
                history.status(),
                history.attemptCount(),
                history.maxAttempts(),
                history.nextRetryAt(),
                history.firstAttemptAt(),
                history.lastAttemptAt(),
                history.manualRetryCount(),
                history.lastManualRetryAt(),
                history.lastManualRetryBy(),
                history.errorType(),
                history.errorMessage(),
                history.providerStatusCode(),
                history.providerMessage(),
                history.createdAt(),
                history.sentAt()
        );
    }
}
