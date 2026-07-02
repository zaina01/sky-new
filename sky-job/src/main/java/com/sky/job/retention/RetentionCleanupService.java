package com.sky.job.retention;

import com.sky.job.audit.LoginLogStore;
import com.sky.job.audit.SystemLogStore;
import com.sky.job.notification.NotificationSendHistoryStore;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.plugin.storage.PluginLogStore;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.system.DynamicRetentionSettings;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

@Service
public class RetentionCleanupService {
    private static final String MODULE = "RETENTION";
    private static final String ACTION = "RETENTION_CLEANUP";

    private final DynamicRetentionSettings properties;
    private final JobExecutionStore jobExecutionStore;
    private final PluginLogStore pluginLogStore;
    private final PluginAuditStore pluginAuditStore;
    private final LoginLogStore loginLogStore;
    private final SystemLogStore systemLogStore;
    private final NotificationSendHistoryStore notificationSendHistoryStore;

    public RetentionCleanupService(
            DynamicRetentionSettings properties,
            JobExecutionStore jobExecutionStore,
            PluginLogStore pluginLogStore,
            PluginAuditStore pluginAuditStore,
            LoginLogStore loginLogStore,
            SystemLogStore systemLogStore,
            NotificationSendHistoryStore notificationSendHistoryStore
    ) {
        this.properties = properties;
        this.jobExecutionStore = jobExecutionStore;
        this.pluginLogStore = pluginLogStore;
        this.pluginAuditStore = pluginAuditStore;
        this.loginLogStore = loginLogStore;
        this.systemLogStore = systemLogStore;
        this.notificationSendHistoryStore = notificationSendHistoryStore;
    }

    public RetentionCleanupResponse runManual(RetentionCleanupRequest request) {
        boolean dryRun = dryRun(request);
        if (!dryRun && (request == null || !Boolean.TRUE.equals(request.confirm()))) {
            throw new IllegalStateException("Retention cleanup requires confirm=true when dryRun=false");
        }
        return run(request == null ? null : request.categories(), dryRun);
    }

    public RetentionCleanupResponse runScheduled() {
        RetentionCleanupResponse response = run(null, properties.dryRun());
        systemLogStore.recordSystem(MODULE, ACTION, "INFO", "SUCCESS", "RETENTION", "scheduled", summary(response));
        return response;
    }

    public String summary(RetentionCleanupResponse response) {
        return "Retention cleanup " + (response.dryRun() ? "dry-run" : "deleted") + ": candidates=" + response.totalCandidateCount() + ", deleted=" + response.totalDeletedCount();
    }

    private RetentionCleanupResponse run(Set<RetentionCleanupCategory> requestedCategories, boolean dryRun) {
        LocalDateTime startedAt = LocalDateTime.now();
        Set<RetentionCleanupCategory> categories = categories(requestedCategories);
        List<RetentionCleanupItemResponse> items = new ArrayList<>();
        for (RetentionCleanupCategory category : categories) {
            items.add(cleanup(category, dryRun, startedAt));
        }
        LocalDateTime finishedAt = LocalDateTime.now();
        long totalCandidates = items.stream().mapToLong(RetentionCleanupItemResponse::candidateCount).sum();
        long totalDeleted = items.stream().mapToLong(RetentionCleanupItemResponse::deletedCount).sum();
        return new RetentionCleanupResponse(properties.enabled(), dryRun, startedAt, finishedAt, totalCandidates, totalDeleted, items);
    }

    private RetentionCleanupItemResponse cleanup(RetentionCleanupCategory category, boolean dryRun, LocalDateTime now) {
        int retentionDays = retentionDays(category);
        LocalDateTime cutoff = now.minusDays(retentionDays);
        String skipped = skippedReason(retentionDays);
        if (skipped != null) {
            return new RetentionCleanupItemResponse(category, cutoff, retentionDays, dryRun, 0, 0, skipped);
        }
        return switch (category) {
            case JOB_EXECUTION -> cleanup(category, cutoff, retentionDays, dryRun, jobExecutionStore::countOlderThan, jobExecutionStore::deleteOlderThan);
            case PLUGIN_TASK_LOG -> cleanup(category, cutoff, retentionDays, dryRun, pluginLogStore::countOlderThan, pluginLogStore::deleteOlderThan);
            case PLUGIN_AUDIT_LOG -> cleanup(category, cutoff, retentionDays, dryRun, pluginAuditStore::countOlderThan, pluginAuditStore::deleteOlderThan);
            case LOGIN_LOG -> cleanup(category, cutoff, retentionDays, dryRun, loginLogStore::countOlderThan, loginLogStore::deleteOlderThan);
            case SYSTEM_LOG -> cleanup(category, cutoff, retentionDays, dryRun, systemLogStore::countOlderThan, systemLogStore::deleteOlderThan);
            case NOTIFICATION_SEND_HISTORY -> cleanup(category, cutoff, retentionDays, dryRun, notificationSendHistoryStore::countOlderThan, notificationSendHistoryStore::deleteOlderThan);
        };
    }

    private RetentionCleanupItemResponse cleanup(
            RetentionCleanupCategory category,
            LocalDateTime cutoff,
            int retentionDays,
            boolean dryRun,
            Function<LocalDateTime, Long> count,
            BiFunction<LocalDateTime, Integer, Integer> delete
    ) {
        long candidates = count.apply(cutoff);
        long deleted = 0;
        if (!dryRun) {
            int batch;
            do {
                batch = delete.apply(cutoff, properties.batchSize());
                deleted += batch;
            } while (batch == properties.batchSize());
        }
        return new RetentionCleanupItemResponse(category, cutoff, retentionDays, dryRun, candidates, deleted, "");
    }

    private String skippedReason(int retentionDays) {
        if (!properties.enabled()) {
            return "Retention cleanup is disabled";
        }
        if (retentionDays < properties.minimumRetentionDays()) {
            return "Retention days is below minimum";
        }
        return null;
    }

    private boolean dryRun(RetentionCleanupRequest request) {
        return request == null || request.dryRun() == null ? properties.dryRun() : request.dryRun();
    }

    private Set<RetentionCleanupCategory> categories(Set<RetentionCleanupCategory> requestedCategories) {
        return requestedCategories == null || requestedCategories.isEmpty() ? EnumSet.allOf(RetentionCleanupCategory.class) : EnumSet.copyOf(requestedCategories);
    }

    private int retentionDays(RetentionCleanupCategory category) {
        return switch (category) {
            case JOB_EXECUTION -> properties.jobExecutionDays();
            case PLUGIN_TASK_LOG -> properties.pluginTaskLogDays();
            case PLUGIN_AUDIT_LOG -> properties.pluginAuditLogDays();
            case LOGIN_LOG -> properties.loginLogDays();
            case SYSTEM_LOG -> properties.systemLogDays();
            case NOTIFICATION_SEND_HISTORY -> properties.notificationSendHistoryDays();
        };
    }
}
