package com.sky.job.notification;

import com.sky.job.notification.mapper.NotificationSendHistoryMapper;
import com.sky.job.notification.mapper.NotificationSendHistoryRow;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Repository
public class NotificationSendHistoryStore {
    private final NotificationSendHistoryMapper mapper;

    public NotificationSendHistoryStore(NotificationSendHistoryMapper mapper) {
        this.mapper = mapper;
    }

    public long insertPending(NotificationSendCommand command, NotificationChannel channel, String title, String content, String preview, int payloadBytes, String labelsJson, int maxAttempts) {
        Map<String, Object> values = new HashMap<>();
        values.put("requestId", value(command.requestId()));
        values.put("sourceType", command.sourceType().name());
        values.put("pluginId", value(command.pluginId()));
        values.put("instanceId", value(command.instanceId()));
        values.put("jobId", value(command.jobId()));
        values.put("executionId", command.executionId());
        values.put("channelId", channel.channelId());
        values.put("channelType", channel.channelType().name());
        values.put("title", title);
        values.put("severity", value(command.severity()));
        values.put("contentPreview", preview);
        values.put("contentPayload", content);
        values.put("payloadBytes", payloadBytes);
        values.put("labelsJson", labelsJson);
        values.put("status", NotificationSendStatus.PENDING.name());
        values.put("maxAttempts", Math.max(maxAttempts, 1));
        mapper.insertPending(values);
        Object id = values.get("id");
        if (id instanceof Number number) {
            return number.longValue();
        }
        throw new IllegalStateException("Notification send history was not saved");
    }

    public List<NotificationSendHistory> claimDue(int limit, String workerId, LocalDateTime now, Duration staleTimeout) {
        restoreStaleSending(now, staleTimeout);
        List<Long> ids = mapper.listDueIds(NotificationSendStatus.PENDING.name(), NotificationSendStatus.RETRY_WAITING.name(), now, Math.max(limit, 1));
        List<NotificationSendHistory> claimed = new ArrayList<>();
        for (Long id : ids) {
            int updated = mapper.claimDue(id, NotificationSendStatus.SENDING.name(), workerId, now, NotificationSendStatus.PENDING.name(), NotificationSendStatus.RETRY_WAITING.name());
            if (updated == 1) {
                findById(id).ifPresent(claimed::add);
            }
        }
        return claimed;
    }

    public void markSendingSuccess(long id, NotificationSendResult result) {
        mapper.markSendingSuccess(id, NotificationSendStatus.SUCCESS.name(), value(result.providerStatusCode()), value(result.providerMessage()));
    }

    public void markSendingRetry(long id, NotificationSendResult result, LocalDateTime nextRetryAt) {
        mapper.markSendingRetry(id, NotificationSendStatus.RETRY_WAITING.name(), nextRetryAt, value(result.errorType()), value(result.errorMessage()), value(result.providerStatusCode()), value(result.providerMessage()));
    }

    public void markSendingFinalFailed(long id, NotificationSendResult result) {
        mapper.markSendingFinalFailed(id, NotificationSendStatus.FAILED.name(), value(result.errorType()), value(result.errorMessage()), value(result.providerStatusCode()), value(result.providerMessage()));
    }

    public int requestManualRetry(long id, String actor, LocalDateTime now) {
        return mapper.requestManualRetry(id, NotificationSendStatus.PENDING.name(), NotificationSendStatus.FAILED.name(), value(actor), now);
    }

    public void markSuccess(long id, NotificationSendResult result) {
        markSendingSuccess(id, result);
    }

    public void markFailed(long id, NotificationSendResult result) {
        markSendingFinalFailed(id, result);
    }

    public void markSkipped(long id, String message) {
        mapper.markSkipped(id, NotificationSendStatus.SKIPPED.name(), value(message));
    }

    public List<NotificationSendHistory> list(
            NotificationSourceType sourceType,
            String channelId,
            NotificationChannelType channelType,
            NotificationSendStatus status,
            String pluginId,
            String jobId,
            Long executionId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer page,
            Integer size
    ) {
        int normalizedPage = normalizedPage(page);
        int normalizedSize = normalizedSize(size);
        return mapper.list(
                        sourceType == null ? null : sourceType.name(),
                        trim(channelId),
                        channelType == null ? null : channelType.name(),
                        status == null ? null : status.name(),
                        trim(pluginId),
                        trim(jobId),
                        executionId,
                        startTime,
                        endTime,
                        normalizedSize,
                        (normalizedPage - 1) * normalizedSize).stream()
                .map(this::map)
                .toList();
    }

    public long count(NotificationSourceType sourceType, String channelId, NotificationChannelType channelType, NotificationSendStatus status, String pluginId, String jobId, Long executionId, LocalDateTime startTime, LocalDateTime endTime) {
        return mapper.count(
                sourceType == null ? null : sourceType.name(),
                trim(channelId),
                channelType == null ? null : channelType.name(),
                status == null ? null : status.name(),
                trim(pluginId),
                trim(jobId),
                executionId,
                startTime,
                endTime);
    }

    public long countByStatus(NotificationSendStatus status) {
        return count(null, null, null, status, null, null, null, null, null);
    }

    public Optional<NotificationSendHistory> findById(long id) {
        return Optional.ofNullable(mapper.findById(id)).map(this::map);
    }

    public long countOlderThan(LocalDateTime cutoff) {
        return mapper.countOlderThan(cutoff);
    }

    public int deleteOlderThan(LocalDateTime cutoff, int limit) {
        return mapper.deleteOlderThan(cutoff, limit);
    }

    private void restoreStaleSending(LocalDateTime now, Duration staleTimeout) {
        mapper.restoreStaleSending(now, now.minus(staleTimeout), NotificationSendStatus.RETRY_WAITING.name(), NotificationSendStatus.SENDING.name(), "WORKER_TIMEOUT", "Notification delivery worker timed out");
    }

    private NotificationSendHistory map(NotificationSendHistoryRow row) {
        return new NotificationSendHistory(
                row.id(),
                row.requestId(),
                NotificationSourceType.valueOf(row.sourceType()),
                row.pluginId(),
                row.instanceId(),
                row.jobId(),
                row.executionId(),
                row.channelId(),
                NotificationChannelType.valueOf(row.channelType()),
                row.title(),
                row.severity(),
                row.contentPreview(),
                row.contentPayload(),
                row.payloadBytes(),
                row.labelsJson(),
                NotificationSendStatus.valueOf(row.status()),
                row.attemptCount(),
                row.maxAttempts(),
                row.nextRetryAt(),
                row.firstAttemptAt(),
                row.lastAttemptAt(),
                row.lockedAt(),
                row.lockedBy(),
                row.manualRetryCount(),
                row.lastManualRetryAt(),
                row.lastManualRetryBy(),
                row.errorType(),
                row.errorMessage(),
                row.providerStatusCode(),
                row.providerMessage(),
                row.createdAt(),
                row.sentAt(),
                row.updatedAt()
        );
    }

    private String value(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
