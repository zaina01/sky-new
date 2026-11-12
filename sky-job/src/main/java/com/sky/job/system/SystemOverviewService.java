package com.sky.job.system;

import com.sky.job.common.mapper.DailyStatusCountRow;
import com.sky.job.common.mapper.StatusCountRow;
import com.sky.job.notification.NotificationSendHistory;
import com.sky.job.notification.NotificationSendHistoryStore;
import com.sky.job.notification.NotificationSendStatus;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.scheduler.ExecutedStatus;
import com.sky.job.scheduler.JobExecution;
import com.sky.job.scheduler.JobExecutionStatus;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.scheduler.JobStatus;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.system.dto.DailyStatusCountResponse;
import com.sky.job.system.dto.OverviewWindowResponse;
import com.sky.job.system.dto.RecentFailuresResponse;
import com.sky.job.system.dto.RecentJobExecutionFailureResponse;
import com.sky.job.system.dto.RecentNotificationFailureResponse;
import com.sky.job.system.dto.StatusCountResponse;
import com.sky.job.system.dto.SystemOverviewResponse;
import com.sky.job.system.dto.TrendOverviewResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class SystemOverviewService {
    private static final int DEFAULT_WINDOW_HOURS = 24;
    private static final int MIN_WINDOW_HOURS = 1;
    private static final int MAX_WINDOW_HOURS = 720;
    private static final int DEFAULT_TREND_DAYS = 7;
    private static final int MIN_TREND_DAYS = 1;
    private static final int MAX_TREND_DAYS = 30;
    private static final int DEFAULT_RECENT_LIMIT = 5;
    private static final int MIN_RECENT_LIMIT = 1;
    private static final int MAX_RECENT_LIMIT = 20;

    private final PluginRuntimeMetadataStore pluginRuntimeStore;
    private final JobStore jobStore;
    private final JobExecutionStore jobExecutionStore;
    private final NotificationSendHistoryStore notificationHistoryStore;
    private final Supplier<LocalDateTime> nowSupplier;
    private final DataScopeService dataScopeService;

    @Autowired
    public SystemOverviewService(
            PluginRuntimeMetadataStore pluginRuntimeStore,
            JobStore jobStore,
            JobExecutionStore jobExecutionStore,
            NotificationSendHistoryStore notificationHistoryStore,
            DataScopeService dataScopeService
    ) {
        this(pluginRuntimeStore, jobStore, jobExecutionStore, notificationHistoryStore, dataScopeService, LocalDateTime::now);
    }

    SystemOverviewService(
            PluginRuntimeMetadataStore pluginRuntimeStore,
            JobStore jobStore,
            JobExecutionStore jobExecutionStore,
            NotificationSendHistoryStore notificationHistoryStore,
            DataScopeService dataScopeService,
            Supplier<LocalDateTime> nowSupplier
    ) {
        this.pluginRuntimeStore = pluginRuntimeStore;
        this.jobStore = jobStore;
        this.jobExecutionStore = jobExecutionStore;
        this.notificationHistoryStore = notificationHistoryStore;
        this.dataScopeService = dataScopeService;
        this.nowSupplier = nowSupplier;
    }

    public SystemOverviewResponse overview() {
        return overview(EffectiveDataScope.all("test"), null, null, null);
    }

    public SystemOverviewResponse overview(AuthenticatedUser user, Integer windowHours, Integer trendDays, Integer recentLimit) {
        return overview(dataScopeService.resolve(user, PermissionNames.SYSTEM_OVERVIEW_READ), windowHours, trendDays, recentLimit);
    }

    private SystemOverviewResponse overview(EffectiveDataScope scope, Integer windowHours, Integer trendDays, Integer recentLimit) {
        int normalizedWindowHours = clamp(windowHours, DEFAULT_WINDOW_HOURS, MIN_WINDOW_HOURS, MAX_WINDOW_HOURS);
        int normalizedTrendDays = clamp(trendDays, DEFAULT_TREND_DAYS, MIN_TREND_DAYS, MAX_TREND_DAYS);
        int normalizedRecentLimit = clamp(recentLimit, DEFAULT_RECENT_LIMIT, MIN_RECENT_LIMIT, MAX_RECENT_LIMIT);

        LocalDateTime endTime = nowSupplier.get();
        LocalDateTime startTime = endTime.minusHours(normalizedWindowHours);
        LocalDate trendEndDate = endTime.toLocalDate().plusDays(1);
        LocalDate trendStartDate = trendEndDate.minusDays(normalizedTrendDays);
        LocalDateTime trendStartTime = trendStartDate.atStartOfDay();
        LocalDateTime trendEndTime = trendEndDate.atStartOfDay();

        return new SystemOverviewResponse(
                pluginRuntimeStore.countRuntimes(null, null, scope),
                jobStore.countByStatus(JobStatus.ENABLED, scope),
                jobExecutionStore.count(null, null, null, JobExecutionStatus.FAILED, scope),
                notificationHistoryStore.countByStatus(NotificationSendStatus.RETRY_WAITING, scope),
                new OverviewWindowResponse(normalizedWindowHours, startTime, endTime, normalizedTrendDays, normalizedRecentLimit),
                statusCounts(PluginRuntimeState.values(), pluginRuntimeStore.countRuntimesByState(scope)),
                statusCounts(JobStatus.values(), jobStore.countByJobStatus(scope)),
                statusCounts(ExecutedStatus.values(), jobStore.countByExecutedStatus(scope)),
                statusCounts(JobExecutionStatus.values(), jobExecutionStore.countByStatusBetween(startTime, endTime, scope)),
                statusCounts(NotificationSendStatus.values(), notificationHistoryStore.countByStatusBetween(startTime, endTime, scope)),
                new TrendOverviewResponse(
                        dailyStatusCounts(JobExecutionStatus.values(), jobExecutionStore.countDailyByStatus(trendStartTime, trendEndTime, scope), trendStartDate, normalizedTrendDays),
                        dailyStatusCounts(NotificationSendStatus.values(), notificationHistoryStore.countDailyByStatus(trendStartTime, trendEndTime, scope), trendStartDate, normalizedTrendDays)
                ),
                new RecentFailuresResponse(
                        jobExecutionStore.listRecentFailures(List.of(JobExecutionStatus.FAILED, JobExecutionStatus.TIMEOUT, JobExecutionStatus.SYSTEM_EXIT), scope, normalizedRecentLimit).stream()
                                .map(this::recentJobExecutionFailure)
                                .toList(),
                        notificationHistoryStore.listRecentFailures(scope, normalizedRecentLimit).stream()
                                .map(this::recentNotificationFailure)
                                .toList()
                )
        );
    }

    private List<StatusCountResponse> statusCounts(Enum<?>[] statuses, List<StatusCountRow> rows) {
        Map<String, Long> counts = rows.stream().collect(Collectors.toMap(StatusCountRow::status, StatusCountRow::total));
        return Arrays.stream(statuses)
                .map(status -> new StatusCountResponse(status.name(), counts.getOrDefault(status.name(), 0L)))
                .toList();
    }

    private List<DailyStatusCountResponse> dailyStatusCounts(Enum<?>[] statuses, List<DailyStatusCountRow> rows, LocalDate startDate, int days) {
        Map<DailyStatusKey, Long> counts = rows.stream().collect(Collectors.toMap(row -> new DailyStatusKey(row.bucketDate(), row.status()), DailyStatusCountRow::total));
        return startDate.datesUntil(startDate.plusDays(days))
                .flatMap(date -> Arrays.stream(statuses)
                        .map(status -> new DailyStatusCountResponse(date, status.name(), counts.getOrDefault(new DailyStatusKey(date, status.name()), 0L))))
                .toList();
    }

    private RecentJobExecutionFailureResponse recentJobExecutionFailure(JobExecution execution) {
        return new RecentJobExecutionFailureResponse(
                execution.id(),
                execution.jobId(),
                execution.jobName(),
                execution.pluginId(),
                execution.instanceId(),
                execution.status(),
                execution.errorType(),
                execution.errorMessage(),
                execution.startedAt(),
                execution.finishedAt(),
                execution.durationMillis(),
                execution.runnerLogPath()
        );
    }

    private RecentNotificationFailureResponse recentNotificationFailure(NotificationSendHistory history) {
        return new RecentNotificationFailureResponse(
                history.id(),
                history.requestId(),
                history.sourceType(),
                history.pluginId(),
                history.instanceId(),
                history.jobId(),
                history.executionId(),
                history.channelId(),
                history.channelType(),
                history.title(),
                history.errorType(),
                history.errorMessage(),
                history.createdAt(),
                history.sentAt()
        );
    }

    private int clamp(Integer value, int defaultValue, int min, int max) {
        int normalized = value == null ? defaultValue : value;
        return Math.min(Math.max(normalized, min), max);
    }

    private record DailyStatusKey(LocalDate date, String status) {
    }
}
