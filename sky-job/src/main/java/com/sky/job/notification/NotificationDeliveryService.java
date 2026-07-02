package com.sky.job.notification;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.provider.NotificationProviderRegistry;
import com.sky.job.system.DynamicNotificationSettings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Service
public class NotificationDeliveryService {
    private static final Logger log = LoggerFactory.getLogger(NotificationDeliveryService.class);
    private static final TypeReference<Map<String, String>> LABELS_TYPE = new TypeReference<>() {
    };

    private final DynamicNotificationSettings properties;
    private final NotificationSendHistoryStore historyStore;
    private final NotificationChannelStore channelStore;
    private final NotificationProviderRegistry providerRegistry;
    private final NotificationPayloadValidator validator;
    private final ObjectMapper objectMapper;
    private final String workerId;

    public NotificationDeliveryService(
            DynamicNotificationSettings properties,
            NotificationSendHistoryStore historyStore,
            NotificationChannelStore channelStore,
            NotificationProviderRegistry providerRegistry,
            NotificationPayloadValidator validator,
            ObjectMapper objectMapper
    ) {
        this.properties = properties;
        this.historyStore = historyStore;
        this.channelStore = channelStore;
        this.providerRegistry = providerRegistry;
        this.validator = validator;
        this.objectMapper = objectMapper;
        this.workerId = "notification-worker-" + System.identityHashCode(this);
    }

    public void runScheduled() {
        List<NotificationSendHistory> histories = historyStore.claimDue(properties.retryBatchSize(), workerId, LocalDateTime.now(), properties.retryStaleTimeout());
        for (NotificationSendHistory history : histories) {
            try {
                process(history);
            } catch (Exception exception) {
                NotificationSendResult result = NotificationSendResult.failed(exception.getClass().getSimpleName(), validator.sanitizeMessage(exception.getMessage()), "", "Notification delivery failed");
                markFailure(history, result);
                log.warn("Failed to process notification delivery: historyId={}, channelId={}, errorType={}", history.id(), history.channelId(), exception.getClass().getSimpleName());
            }
        }
    }

    private void process(NotificationSendHistory history) {
        if (history.contentPayload() == null || history.contentPayload().isBlank()) {
            historyStore.markSendingFinalFailed(history.id(), NotificationSendResult.failed("PAYLOAD_UNAVAILABLE", "Notification payload is not retryable", "", ""));
            return;
        }
        NotificationChannel channel = channelStore.findByChannelId(history.channelId()).orElse(null);
        if (channel == null || !channel.enabled()) {
            historyStore.markSendingFinalFailed(history.id(), NotificationSendResult.failed("CHANNEL_UNAVAILABLE", "Notification channel not found or disabled", "", ""));
            return;
        }
        NotificationSendCommand command = new NotificationSendCommand(
                history.sourceType(),
                history.requestId(),
                history.pluginId(),
                history.instanceId(),
                history.jobId(),
                history.executionId(),
                List.of(history.channelId()),
                history.title(),
                history.contentPayload(),
                history.severity(),
                labels(history.labelsJson())
        );
        try {
            NotificationSendResult result = sanitize(providerRegistry.get(channel.channelType()).send(channel, command));
            if (result.success()) {
                historyStore.markSendingSuccess(history.id(), result);
            } else {
                markFailure(history, result);
            }
        } catch (RuntimeException exception) {
            NotificationSendResult result = NotificationSendResult.failed(exception.getClass().getSimpleName(), validator.sanitizeMessage(exception.getMessage()), "", "Notification provider failed");
            markFailure(history, result);
        }
    }

    private void markFailure(NotificationSendHistory history, NotificationSendResult result) {
        int nextAttemptCount = history.attemptCount() + 1;
        if (nextAttemptCount >= history.maxAttempts()) {
            historyStore.markSendingFinalFailed(history.id(), result);
            return;
        }
        historyStore.markSendingRetry(history.id(), result, LocalDateTime.now().plus(backoff(nextAttemptCount)));
    }

    private Duration backoff(int attemptCount) {
        long multiplier = 1L << Math.min(Math.max(attemptCount - 1, 0), 20);
        Duration delay = properties.retryInitialDelay().multipliedBy(multiplier);
        return delay.compareTo(properties.retryMaxDelay()) > 0 ? properties.retryMaxDelay() : delay;
    }

    private NotificationSendResult sanitize(NotificationSendResult result) {
        return new NotificationSendResult(
                result.success(),
                validator.sanitizeMessage(result.providerStatusCode()),
                validator.sanitizeMessage(result.providerMessage()),
                validator.sanitizeMessage(result.errorType()),
                validator.sanitizeMessage(result.errorMessage())
        );
    }

    private Map<String, String> labels(String labelsJson) {
        if (labelsJson == null || labelsJson.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(labelsJson, LABELS_TYPE);
        } catch (JsonProcessingException exception) {
            return Map.of();
        }
    }
}
