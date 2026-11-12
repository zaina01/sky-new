package com.sky.job.notification;

import com.sky.job.common.mapper.DailyStatusCountRow;
import com.sky.job.common.mapper.StatusCountRow;
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
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;

@Repository
public class NotificationSendHistoryStore {
    private final NotificationSendHistoryMapper mapper;
    private final JobStore jobStore;
    private final PluginRuntimeMetadataStore runtimeStore;

    public NotificationSendHistoryStore(NotificationSendHistoryMapper mapper, JobStore jobStore, PluginRuntimeMetadataStore runtimeStore) {
        this.mapper = mapper;
        this.jobStore = jobStore;
        this.runtimeStore = runtimeStore;
    }

    public long insertPending(NotificationSendCommand command, NotificationChannel channel, String title, String content, String preview, int payloadBytes, String labelsJson, int maxAttempts) {
        Map<String, Object> values = new HashMap<>();
        values.put("requestId", value(command.requestId()));
        values.put("sourceType", command.sourceType().name());
        values.put("installationId", value(command.installationId()));
        values.put("pluginId", value(command.pluginId()));
        values.put("instanceId", value(command.instanceId()));
        values.put("jobId", value(command.jobId()));
        values.put("executionId", command.executionId());
        Ownership source = sourceOwnership(command);
        values.put("sourceOwnerUserId", source.userId());
        values.put("sourceOwnerDepartmentId", source.departmentId());
        values.put("channelOwnerUserId", channel.ownerUserId());
        values.put("channelOwnerDepartmentId", channel.ownerDepartmentId());
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
                findByIdUnscoped(id).ifPresent(claimed::add);
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
            String installationId,
            String pluginId,
            String jobId,
            Long executionId,
            LocalDateTime startTime,
            LocalDateTime endTime,
            EffectiveDataScope scope,
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
                        trim(installationId),
                        trim(pluginId),
                        trim(jobId),
                        executionId,
                        startTime,
                        endTime,
                        scope,
                        normalizedSize,
                        (normalizedPage - 1) * normalizedSize).stream()
                .map(this::map)
                .toList();
    }

    public long count(NotificationSourceType sourceType, String channelId, NotificationChannelType channelType, NotificationSendStatus status, String installationId, String pluginId, String jobId, Long executionId, LocalDateTime startTime, LocalDateTime endTime, EffectiveDataScope scope) {
        return mapper.count(
                sourceType == null ? null : sourceType.name(),
                trim(channelId),
                channelType == null ? null : channelType.name(),
                status == null ? null : status.name(),
                trim(installationId),
                trim(pluginId),
                trim(jobId),
                executionId,
                startTime,
                endTime,
                scope);
    }

    public long countByStatus(NotificationSendStatus status, EffectiveDataScope scope) {
        return count(null, null, null, status, null, null, null, null, null, null, scope);
    }

    public Optional<NotificationSendHistory> findById(long id, EffectiveDataScope scope) {
        return Optional.ofNullable(mapper.findById(id, scope)).map(this::map);
    }

    public Optional<NotificationSendHistory> findByIdUnscoped(long id) {
        return Optional.ofNullable(mapper.findByIdUnscoped(id)).map(this::map);
    }

    public List<StatusCountRow> countByStatusBetween(LocalDateTime startTime, LocalDateTime endTime, EffectiveDataScope scope) {
        return mapper.countByStatusBetween(startTime, endTime, scope);
    }

    public List<DailyStatusCountRow> countDailyByStatus(LocalDateTime startTime, LocalDateTime endTime, EffectiveDataScope scope) {
        return mapper.countDailyByStatus(startTime, endTime, scope);
    }

    public List<NotificationSendHistory> listRecentFailures(EffectiveDataScope scope, int limit) {
        return mapper.listRecentFailures(NotificationSendStatus.FAILED.name(), scope, limit).stream().map(this::map).toList();
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
                row.installationId(),
                row.pluginId(),
                row.instanceId(),
                row.jobId(),
                row.executionId(),
                row.sourceOwnerUserId(),
                row.sourceOwnerDepartmentId(),
                row.channelOwnerUserId(),
                row.channelOwnerDepartmentId(),
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

    private Ownership sourceOwnership(NotificationSendCommand command) {
        if (command.sourceOwnerUserId() != null || command.sourceOwnerDepartmentId() != null) {
            return new Ownership(command.sourceOwnerUserId(), command.sourceOwnerDepartmentId());
        }
        if (command.jobId() != null && !command.jobId().isBlank()) {
            try {
                var job = jobStore.findByIdUnscoped(Long.parseLong(command.jobId())).orElse(null);
                if (job != null) return new Ownership(job.ownerUserId(), job.ownerDepartmentId());
            } catch (NumberFormatException ignored) {
            }
        }
        if (command.installationId() != null && !command.installationId().isBlank()) {
            var runtime = runtimeStore.findRuntimeByInstallationId(command.installationId()).orElse(null);
            if (runtime != null) return new Ownership(runtime.ownerUserId(), runtime.ownerDepartmentId());
        }
        return new Ownership(null, null);
    }

    private record Ownership(String userId, Long departmentId) {
    }
}
