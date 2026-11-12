package com.sky.job.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.audit.SystemLogStore;
import com.sky.job.notification.provider.NotificationDeliveryPart;
import com.sky.job.notification.provider.NotificationProvider;
import com.sky.job.notification.provider.NotificationProviderRegistry;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.scheduler.ExecutedStatus;
import com.sky.job.scheduler.JobExecution;
import com.sky.job.scheduler.JobExecutionStatus;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.scheduler.JobTriggerType;
import com.sky.job.scheduler.JobStatus;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.MisfirePolicy;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.ScheduledJob;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.system.DynamicNotificationSettings;
import com.sky.job.web.ResourceNotFoundException;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceAuthorizationTest {
    private static final String INSTALLATION_ID = "installation-a";
    private static final long JOB_ID = 42L;

    @Mock private DynamicNotificationSettings properties;
    @Mock private NotificationChannelStore channelStore;
    @Mock private NotificationRuleStore ruleStore;
    @Mock private NotificationSendHistoryStore historyStore;
    @Mock private NotificationProviderRegistry providerRegistry;
    @Mock private NotificationProvider notificationProvider;
    @Mock private SystemLogStore systemLogStore;
    @Mock private PluginAuditStore pluginAuditStore;
    @Mock private PluginRuntimeMetadataStore runtimeStore;
    @Mock private JobStore jobStore;
    @Mock private JobExecutionStore executionStore;
    @Mock private DataScopeService dataScopeService;
    @Mock private HttpServletRequest httpRequest;

    @Test
    void taskRunnerSeesOnlyIntersectionOfInstallationPoolAndJobSubset() {
        arrangeTaskChannels(List.of("installation-only", "shared"), List.of("shared", "job-only"));
        when(channelStore.listByIdsUnscoped(anyList())).thenReturn(List.of(channel("shared", true)));

        List<NotificationChannel> channels = service().listAuthorizedChannels(taskPrincipal());

        assertThat(channels).extracting(NotificationChannel::channelId).containsExactly("shared");
        ArgumentCaptor<List<String>> ids = listCaptor();
        verify(channelStore).listByIdsUnscoped(ids.capture());
        assertThat(ids.getValue()).containsExactly("shared");
    }

    @Test
    void serviceRunnerSeesItsWholeInstallationPool() {
        when(runtimeStore.notificationChannelIds(INSTALLATION_ID)).thenReturn(List.of("first", "second"));
        when(channelStore.listByIdsUnscoped(anyList()))
                .thenReturn(List.of(channel("first", true), channel("second", false)));

        List<NotificationChannel> channels = service().listAuthorizedChannels(servicePrincipal());

        assertThat(channels).extracting(NotificationChannel::channelId).containsExactly("first", "second");
        verify(jobStore, never()).findByIdUnscoped(anyLong());
        verify(jobStore, never()).notificationChannelIds(anyLong());
    }

    @Test
    void anyUnauthorizedChannelRejectsTheWholePluginRequest() {
        arrangeTaskChannels(List.of("allowed", "installation-only"), List.of("allowed", "job-only"));

        assertStatus(
                () -> service().sendFromPlugin(taskPrincipal(), command(List.of("allowed", "job-only"))),
                Status.Code.PERMISSION_DENIED,
                "outside the runtime whitelist"
        );

        verify(channelStore, never()).listByIdsUnscoped(anyList());
        verify(historyStore, never()).insertPending(any(), any(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt());
    }

    @Test
    void disabledAuthorizedChannelCreatesSkippedHistory() {
        arrangeTaskChannels(List.of("disabled"), List.of("disabled"));
        NotificationChannel disabled = channel("disabled", false);
        when(channelStore.listByIdsUnscoped(anyList())).thenReturn(List.of(disabled));
        when(historyStore.insertPending(any(), eq(disabled), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt()))
                .thenReturn(101L);

        List<NotificationSendReceipt> receipts = service().sendFromPlugin(taskPrincipal(), command(List.of("disabled")));

        assertThat(receipts).containsExactly(new NotificationSendReceipt(
                "disabled", NotificationSendStatus.SKIPPED, "Notification channel is disabled", 101L));
        verify(historyStore).markSkipped(101L, "Notification channel is disabled");
    }

    @Test
    void pluginSendUsesJobOwnershipSnapshotRatherThanCallerPayload() {
        arrangeTaskChannels(List.of("shared"), List.of("shared"));
        NotificationChannel channel = channel("shared", true);
        when(channelStore.listByIdsUnscoped(anyList())).thenReturn(List.of(channel));
        when(historyStore.insertPending(any(), eq(channel), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt()))
                .thenReturn(102L);

        service().sendFromPlugin(taskPrincipal(), command(List.of("shared")));

        ArgumentCaptor<NotificationSendCommand> saved = ArgumentCaptor.forClass(NotificationSendCommand.class);
        verify(historyStore).insertPending(saved.capture(), eq(channel), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt());
        assertThat(saved.getValue().sourceOwnerUserId()).isEqualTo("job-owner");
        assertThat(saved.getValue().sourceOwnerDepartmentId()).isEqualTo(10L);
        assertThat(saved.getValue().installationId()).isEqualTo(INSTALLATION_ID);
        assertThat(saved.getValue().executionId()).isEqualTo(420L);
    }

    @Test
    void multipartPlanCreatesIndependentOrderedHistoriesAndReceipts() {
        arrangeTaskChannels(List.of("shared"), List.of("shared"));
        NotificationChannel channel = channel("shared", true);
        when(channelStore.listByIdsUnscoped(anyList())).thenReturn(List.of(channel));
        NotificationService service = service();
        doReturn(List.of(
                new NotificationDeliveryPart("Title [1/2]", "Con", 1, 2),
                new NotificationDeliveryPart("Title [2/2]", "tent", 2, 2)
        )).when(notificationProvider).plan(eq(channel), any());
        when(historyStore.insertPending(any(), eq(channel), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt()))
                .thenReturn(201L, 202L);

        List<NotificationSendReceipt> receipts = service.sendFromPlugin(taskPrincipal(), command(List.of("shared")));

        assertThat(receipts).containsExactly(
                new NotificationSendReceipt("shared", NotificationSendStatus.PENDING, "Notification chunk 1/2 queued", 201L),
                new NotificationSendReceipt("shared", NotificationSendStatus.PENDING, "Notification chunk 2/2 queued", 202L)
        );
        ArgumentCaptor<NotificationSendCommand> commands = ArgumentCaptor.forClass(NotificationSendCommand.class);
        ArgumentCaptor<String> titles = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> contents = ArgumentCaptor.forClass(String.class);
        verify(historyStore, times(2)).insertPending(
                commands.capture(), eq(channel), titles.capture(), contents.capture(), anyString(), anyInt(), anyString(), anyInt());
        assertThat(titles.getAllValues()).containsExactly("Title [1/2]", "Title [2/2]");
        assertThat(contents.getAllValues()).containsExactly("Con", "tent");
        assertThat(commands.getAllValues()).allSatisfy(saved -> {
            assertThat(saved.sourceOwnerUserId()).isEqualTo("job-owner");
            assertThat(saved.sourceOwnerDepartmentId()).isEqualTo(10L);
            assertThat(saved.labels()).isEmpty();
        });
    }

    @Test
    void rejectedDeliveryPlanDoesNotInsertAnyHistory() {
        arrangeTaskChannels(List.of("shared"), List.of("shared"));
        NotificationChannel channel = channel("shared", true);
        when(channelStore.listByIdsUnscoped(anyList())).thenReturn(List.of(channel));
        NotificationService service = service();
        doThrow(new IllegalArgumentException("WeChat webhook notification requires more than 20 chunks"))
                .when(notificationProvider).plan(eq(channel), any());

        assertThatThrownBy(() -> service.sendFromPlugin(taskPrincipal(), command(List.of("shared"))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WeChat webhook notification requires more than 20 chunks");

        verify(historyStore, never()).insertPending(any(), any(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt());
        verify(pluginAuditStore, never()).insert(anyString(), anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void forgedJobForAnotherInstallationIsRejected() {
        when(executionStore.findByIdUnscoped(420L)).thenReturn(Optional.of(execution()));
        when(jobStore.findByIdUnscoped(JOB_ID)).thenReturn(Optional.of(job("other-installation")));

        assertStatus(
                () -> service().sendFromPlugin(taskPrincipal(), command(List.of("channel"))),
                Status.Code.PERMISSION_DENIED,
                "Job installation mismatch"
        );

        verify(runtimeStore, never()).notificationChannelIds(anyString());
        verify(historyStore, never()).insertPending(any(), any(), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt());
    }

    @Test
    void missingTokenBoundExecutionIsRejected() {
        when(executionStore.findByIdUnscoped(420L)).thenReturn(Optional.empty());

        assertStatus(
                () -> service().sendFromPlugin(taskPrincipal(), command(List.of("channel"))),
                Status.Code.PERMISSION_DENIED,
                "Execution context is not available"
        );

        verify(runtimeStore, never()).notificationChannelIds(anyString());
        verify(jobStore, never()).notificationChannelIds(anyLong());
    }

    @Test
    void hostCompletionRuleDoesNotDependOnPluginPushWhitelist() {
        NotificationRule rule = rule("outside-push-pool");
        NotificationChannel channel = channel("outside-push-pool", true);
        when(ruleStore.listEnabledCandidates(
                NotificationEventType.JOB_EXECUTION_FAILED, INSTALLATION_ID, Long.toString(JOB_ID), "ERROR"))
                .thenReturn(List.of(rule));
        when(channelStore.listByIdsUnscoped(List.of("outside-push-pool"))).thenReturn(List.of(channel));
        when(historyStore.insertPending(any(), eq(channel), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt()))
                .thenReturn(103L);

        service().sendHostAlert(new HostNotificationEvent(
                NotificationEventType.JOB_EXECUTION_FAILED,
                "Job failed",
                "failure detail",
                "ERROR",
                420L,
                INSTALLATION_ID,
                "demo",
                "runner-1",
                Long.toString(JOB_ID),
                "execution-owner",
                15L,
                Map.of("status", "FAILED")
        ));

        ArgumentCaptor<NotificationSendCommand> saved = ArgumentCaptor.forClass(NotificationSendCommand.class);
        verify(historyStore).insertPending(saved.capture(), eq(channel), anyString(), anyString(), anyString(), anyInt(), anyString(), anyInt());
        assertThat(saved.getValue().sourceType()).isEqualTo(NotificationSourceType.HOST_ALERT);
        assertThat(saved.getValue().sourceOwnerUserId()).isEqualTo("execution-owner");
        assertThat(saved.getValue().sourceOwnerDepartmentId()).isEqualTo(15L);
        verify(runtimeStore, never()).notificationChannelIds(anyString());
        verify(jobStore, never()).notificationChannelIds(anyLong());
        verify(jobStore, never()).findByIdUnscoped(anyLong());
    }

    @Test
    void sourceSideHistoryVisibilityDoesNotBypassCurrentChannelScopeForRetry() {
        AuthenticatedUser user = new AuthenticatedUser(
                "source-owner", "alice", "", 10L, true, Set.of("OPERATOR"),
                Set.of(PermissionNames.NOTIFICATION_HISTORY_RETRY));
        EffectiveDataScope retryScope = new EffectiveDataScope("source-owner", false, true, Set.of());
        NotificationSendHistory history = mock(NotificationSendHistory.class);
        NotificationChannel currentChannel = channel("channel-a", true);
        when(dataScopeService.resolve(user, PermissionNames.NOTIFICATION_HISTORY_RETRY)).thenReturn(retryScope);
        when(historyStore.findById(7L, retryScope)).thenReturn(Optional.of(history));
        when(history.channelId()).thenReturn("channel-a");
        when(channelStore.findByChannelIdUnscoped("channel-a")).thenReturn(Optional.of(currentChannel));
        when(dataScopeService.allows(retryScope, currentChannel.ownerUserId(), currentChannel.ownerDepartmentId()))
                .thenReturn(false);

        assertThatThrownBy(() -> service().manualRetry(7L, user, httpRequest))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Notification send history not found");

        verify(historyStore, never()).requestManualRetry(eq(7L), anyString(), any(LocalDateTime.class));
    }

    private NotificationService service() {
        lenient().when(properties.maxTitleBytes()).thenReturn(4_096);
        lenient().when(properties.maxContentBytes()).thenReturn(64 * 1_024);
        lenient().when(properties.maxPreviewChars()).thenReturn(256);
        lenient().when(properties.retryMaxAttempts()).thenReturn(3);
        lenient().when(providerRegistry.get(any())).thenReturn(notificationProvider);
        lenient().when(notificationProvider.plan(any(), any())).thenAnswer(invocation -> {
            NotificationSendCommand planned = invocation.getArgument(1);
            return List.of(NotificationDeliveryPart.single(planned.title(), planned.content()));
        });
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationPayloadValidator validator = new NotificationPayloadValidator(
                properties,
                new NotificationSecretMasker(objectMapper)
        );
        return new NotificationService(
                properties,
                channelStore,
                ruleStore,
                historyStore,
                providerRegistry,
                validator,
                systemLogStore,
                pluginAuditStore,
                objectMapper,
                runtimeStore,
                jobStore,
                executionStore,
                dataScopeService
        );
    }

    private void arrangeTaskChannels(List<String> installationChannels, List<String> jobChannels) {
        when(runtimeStore.notificationChannelIds(INSTALLATION_ID)).thenReturn(installationChannels);
        when(jobStore.notificationChannelIds(JOB_ID)).thenReturn(jobChannels);
        when(jobStore.findByIdUnscoped(JOB_ID)).thenReturn(Optional.of(job(INSTALLATION_ID)));
        lenient().when(executionStore.findByIdUnscoped(420L)).thenReturn(Optional.of(execution()));
    }

    private PluginRuntimePrincipal taskPrincipal() {
        return principal(JOB_ID, 420L, Set.of(PluginCapability.TASK, PluginCapability.NOTIFICATION));
    }

    private PluginRuntimePrincipal servicePrincipal() {
        return principal(null, null, Set.of(PluginCapability.SERVICE, PluginCapability.NOTIFICATION));
    }

    private PluginRuntimePrincipal principal(Long jobId, Long executionId, Set<PluginCapability> capabilities) {
        return new PluginRuntimePrincipal(
                INSTALLATION_ID,
                "demo",
                "runner-1",
                jobId,
                executionId,
                capabilities,
                Instant.now(),
                Instant.now().plusSeconds(60)
        );
    }

    private NotificationSendCommand command(List<String> channelIds) {
        return new NotificationSendCommand(
                NotificationSourceType.PLUGIN_RUNTIME,
                "request-1",
                INSTALLATION_ID,
                "demo",
                "runner-1",
                Long.toString(JOB_ID),
                420L,
                "forged-owner",
                999L,
                channelIds,
                "Title",
                "Content",
                "INFO",
                Map.of()
        );
    }

    private ScheduledJob job(String installationId) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new ScheduledJob(
                JOB_ID, "job", installationId, "demo", "Demo", "0 * * * * ?",
                JobStatus.ENABLED, MisfirePolicy.DO_NOTHING, false, "{}", 10_000,
                ExecutedStatus.IDLE, "", "job-owner", "alice", 10L, "Department",
                "alice", "alice", now, now
        );
    }

    private JobExecution execution() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new JobExecution(
                420L, JOB_ID, INSTALLATION_ID, "demo", "runner-1", "request-1",
                JobExecutionStatus.RUNNING, JobTriggerType.MANUAL, "job", now, null, null,
                "", "{}", "", "", "runner.log", "job-owner", 10L, now
        );
    }

    private NotificationChannel channel(String channelId, boolean enabled) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new NotificationChannel(
                1L, channelId, NotificationChannelType.WEBHOOK, channelId, enabled, "{}", "",
                "channel-owner", "bob", 20L, "Channel Department", "bob", "bob", now, now
        );
    }

    private NotificationRule rule(String channelId) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new NotificationRule(
                1L, "rule-1", "Failure rule", true, NotificationEventType.JOB_EXECUTION_FAILED,
                INSTALLATION_ID, "demo", Long.toString(JOB_ID), "ERROR", List.of(channelId), "",
                "rule-owner", "carol", 30L, "Rule Department", "carol", "carol", now, now
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private ArgumentCaptor<List<String>> listCaptor() {
        return (ArgumentCaptor) ArgumentCaptor.forClass(List.class);
    }

    private void assertStatus(Runnable action, Status.Code code, String description) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception -> {
                    assertThat(exception.getStatus().getCode()).isEqualTo(code);
                    assertThat(exception.getStatus().getDescription()).contains(description);
                });
    }
}
