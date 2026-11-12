package com.sky.job.system;

import com.sky.job.common.mapper.DailyStatusCountRow;
import com.sky.job.common.mapper.StatusCountRow;
import com.sky.job.notification.NotificationChannelType;
import com.sky.job.notification.NotificationSendHistory;
import com.sky.job.notification.NotificationSendHistoryStore;
import com.sky.job.notification.NotificationSendStatus;
import com.sky.job.notification.NotificationSourceType;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.scheduler.ExecutedStatus;
import com.sky.job.scheduler.JobExecution;
import com.sky.job.scheduler.JobExecutionStatus;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.scheduler.JobStatus;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.JobTriggerType;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.system.dto.SystemOverviewResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SystemOverviewServiceTest {
    @Mock private PluginRuntimeMetadataStore pluginRuntimeStore;
    @Mock private JobStore jobStore;
    @Mock private JobExecutionStore jobExecutionStore;
    @Mock private NotificationSendHistoryStore notificationHistoryStore;
    @Mock private DataScopeService dataScopeService;

    @Test
    void appliesOneScopeToEveryDashboardCounterAndTrend() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 10, 0);
        EffectiveDataScope scope = EffectiveDataScope.all("test");
        SystemOverviewService service = service(now);
        when(pluginRuntimeStore.countRuntimes(null, null, scope)).thenReturn(12L);
        when(jobStore.countByStatus(JobStatus.ENABLED, scope)).thenReturn(8L);
        when(jobExecutionStore.count(null, null, null, JobExecutionStatus.FAILED, scope)).thenReturn(4L);
        when(notificationHistoryStore.countByStatus(NotificationSendStatus.RETRY_WAITING, scope)).thenReturn(2L);
        when(pluginRuntimeStore.countRuntimesByState(scope)).thenReturn(List.of(new StatusCountRow(PluginRuntimeState.RUNNING.name(), 2L)));
        when(jobStore.countByJobStatus(scope)).thenReturn(List.of(new StatusCountRow(JobStatus.ENABLED.name(), 8L)));
        when(jobStore.countByExecutedStatus(scope)).thenReturn(List.of(new StatusCountRow(ExecutedStatus.RUNNING.name(), 1L)));
        when(jobExecutionStore.countByStatusBetween(now.minusHours(24), now, scope)).thenReturn(List.of(new StatusCountRow(JobExecutionStatus.SUCCESS.name(), 5L)));
        when(notificationHistoryStore.countByStatusBetween(now.minusHours(24), now, scope)).thenReturn(List.of(new StatusCountRow(NotificationSendStatus.FAILED.name(), 1L)));
        when(jobExecutionStore.countDailyByStatus(LocalDate.of(2026, 6, 26).atStartOfDay(), LocalDate.of(2026, 7, 3).atStartOfDay(), scope))
                .thenReturn(List.of(new DailyStatusCountRow(LocalDate.of(2026, 7, 2), JobExecutionStatus.SUCCESS.name(), 3L)));
        when(notificationHistoryStore.countDailyByStatus(LocalDate.of(2026, 6, 26).atStartOfDay(), LocalDate.of(2026, 7, 3).atStartOfDay(), scope))
                .thenReturn(List.of(new DailyStatusCountRow(LocalDate.of(2026, 7, 1), NotificationSendStatus.FAILED.name(), 2L)));
        when(jobExecutionStore.listRecentFailures(List.of(JobExecutionStatus.FAILED, JobExecutionStatus.TIMEOUT, JobExecutionStatus.SYSTEM_EXIT), scope, 5)).thenReturn(List.of());
        when(notificationHistoryStore.listRecentFailures(scope, 5)).thenReturn(List.of());

        SystemOverviewResponse response = service.overview();

        assertThat(response.uploadedPlugins()).isEqualTo(12L);
        assertThat(response.enabledJobs()).isEqualTo(8L);
        assertThat(response.failedExecutions()).isEqualTo(4L);
        assertThat(response.pendingRetryNotifications()).isEqualTo(2L);
        assertThat(response.pluginRuntimeStates()).contains(status(PluginRuntimeState.RUNNING.name(), 2));
        assertThat(response.executionStatusesInWindow()).contains(status(JobExecutionStatus.SUCCESS.name(), 5));
        assertThat(response.notificationStatusesInWindow()).contains(status(NotificationSendStatus.FAILED.name(), 1));
    }

    @Test
    void resolvesPermissionSpecificScopeAndClampsParameters() {
        LocalDateTime now = LocalDateTime.of(2026, 7, 2, 10, 0);
        EffectiveDataScope scope = new EffectiveDataScope("user-1", false, true, Set.of(7L));
        AuthenticatedUser user = new AuthenticatedUser("user-1", "alice", "", 7L, true, Set.of("REPORTER"), Set.of(PermissionNames.SYSTEM_OVERVIEW_READ));
        when(dataScopeService.resolve(user, PermissionNames.SYSTEM_OVERVIEW_READ)).thenReturn(scope);
        SystemOverviewService service = service(now);
        stubEmptyOverview(now, scope, 720, 30, 20);
        JobExecution execution = execution(now);
        NotificationSendHistory notification = notification(now);
        when(jobExecutionStore.listRecentFailures(List.of(JobExecutionStatus.FAILED, JobExecutionStatus.TIMEOUT, JobExecutionStatus.SYSTEM_EXIT), scope, 20)).thenReturn(List.of(execution));
        when(notificationHistoryStore.listRecentFailures(scope, 20)).thenReturn(List.of(notification));

        SystemOverviewResponse response = service.overview(user, 1000, 100, 100);

        assertThat(response.window().windowHours()).isEqualTo(720);
        assertThat(response.window().trendDays()).isEqualTo(30);
        assertThat(response.window().recentLimit()).isEqualTo(20);
        assertThat(response.recentFailures().jobExecutions()).singleElement().satisfies(failure -> {
            assertThat(failure.id()).isEqualTo(11L);
            assertThat(failure.status()).isEqualTo(JobExecutionStatus.TIMEOUT);
        });
        assertThat(response.recentFailures().notifications()).singleElement().satisfies(failure -> {
            assertThat(failure.id()).isEqualTo(33L);
            assertThat(failure.channelType()).isEqualTo(NotificationChannelType.WEBHOOK);
        });
        ArgumentCaptor<List<JobExecutionStatus>> statuses = ArgumentCaptor.captor();
        verify(jobExecutionStore).listRecentFailures(statuses.capture(), eq(scope), eq(20));
        assertThat(statuses.getValue()).containsExactly(JobExecutionStatus.FAILED, JobExecutionStatus.TIMEOUT, JobExecutionStatus.SYSTEM_EXIT);
    }

    private SystemOverviewService service(LocalDateTime now) {
        return new SystemOverviewService(pluginRuntimeStore, jobStore, jobExecutionStore, notificationHistoryStore, dataScopeService, () -> now);
    }

    private void stubEmptyOverview(LocalDateTime now, EffectiveDataScope scope, int hours, int days, int recentLimit) {
        when(pluginRuntimeStore.countRuntimes(null, null, scope)).thenReturn(0L);
        when(jobStore.countByStatus(JobStatus.ENABLED, scope)).thenReturn(0L);
        when(jobExecutionStore.count(null, null, null, JobExecutionStatus.FAILED, scope)).thenReturn(0L);
        when(notificationHistoryStore.countByStatus(NotificationSendStatus.RETRY_WAITING, scope)).thenReturn(0L);
        when(pluginRuntimeStore.countRuntimesByState(scope)).thenReturn(List.of());
        when(jobStore.countByJobStatus(scope)).thenReturn(List.of());
        when(jobStore.countByExecutedStatus(scope)).thenReturn(List.of());
        when(jobExecutionStore.countByStatusBetween(now.minusHours(hours), now, scope)).thenReturn(List.of());
        when(notificationHistoryStore.countByStatusBetween(now.minusHours(hours), now, scope)).thenReturn(List.of());
        LocalDate end = now.toLocalDate().plusDays(1);
        LocalDate start = end.minusDays(days);
        when(jobExecutionStore.countDailyByStatus(start.atStartOfDay(), end.atStartOfDay(), scope)).thenReturn(List.of());
        when(notificationHistoryStore.countDailyByStatus(start.atStartOfDay(), end.atStartOfDay(), scope)).thenReturn(List.of());
        // These are overridden by tests that need non-empty recent failures.
        org.mockito.Mockito.lenient().when(jobExecutionStore.listRecentFailures(List.of(JobExecutionStatus.FAILED, JobExecutionStatus.TIMEOUT, JobExecutionStatus.SYSTEM_EXIT), scope, recentLimit)).thenReturn(List.of());
        org.mockito.Mockito.lenient().when(notificationHistoryStore.listRecentFailures(scope, recentLimit)).thenReturn(List.of());
    }

    private JobExecution execution(LocalDateTime now) {
        return new JobExecution(
                11L, 22L, "installation-a", "plugin-a", "instance-a", "request-a",
                JobExecutionStatus.TIMEOUT, JobTriggerType.MANUAL, "Job A", now.minusMinutes(5), now,
                300_000L, "timeout", "{}", "TIMEOUT", "Task timed out", "logs/a.log",
                "user-1", 7L, now.minusMinutes(5));
    }

    private NotificationSendHistory notification(LocalDateTime now) {
        return new NotificationSendHistory(
                33L, "request-b", NotificationSourceType.HOST_ALERT, "installation-b", "plugin-b", "instance-b",
                "22", 11L, "user-1", 7L, "channel-user", 8L, "channel-a", NotificationChannelType.WEBHOOK,
                "Alert", "ERROR", "preview", "payload", 7, "{}", NotificationSendStatus.FAILED,
                1, 3, null, now.minusMinutes(2), now.minusMinutes(2), null, "", 0, null, "",
                "HTTP_ERROR", "failed", "500", "server error", now.minusMinutes(2), now.minusMinutes(1), now.minusMinutes(1));
    }

    private static com.sky.job.system.dto.StatusCountResponse status(String status, long count) {
        return new com.sky.job.system.dto.StatusCountResponse(status, count);
    }
}
