package com.sky.job.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.execution.PluginTaskInvoker;
import com.sky.job.plugin.runtime.PluginRuntimeHandle;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeService;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.plugin.runtime.ServicePluginIdleTracker;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.scheduler.JobExecution;
import com.sky.job.scheduler.JobExecutionStatus;
import com.sky.job.scheduler.JobExecutionStore;
import com.sky.job.scheduler.JobTriggerType;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.plugin.protocol.PluginServiceCallRequest;
import com.sky.plugin.protocol.PluginServiceRunRequest;
import com.sky.plugin.protocol.PluginServiceRunResponse;
import io.grpc.Status;
import io.grpc.StatusRuntimeException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginServiceCallCoordinatorTest {
    private static final String CALLER_INSTALLATION = "caller-installation";
    private static final String TARGET_INSTALLATION = "target-installation";
    private static final String TARGET_PLUGIN = "target-plugin";
    private static final String OPERATION = "lookup";
    private static final String CALLER_INSTANCE = "caller-instance-job-42-exec-run-1";

    @Mock private PluginRuntimeMetadataStore runtimeStore;
    @Mock private JobExecutionStore executionStore;
    @Mock private PluginRuntimeService runtimeService;
    @Mock private PluginTaskInvoker taskInvoker;
    @Mock private PluginServicePermissionStore permissionStore;
    @Mock private ServicePluginIdleTracker idleTracker;
    @Mock private PluginServiceCallLogStore callLogStore;
    @Mock private PluginAuditStore auditStore;
    @Mock private ProcessPluginRuntimeProperties runtimeProperties;
    @Mock private PluginRuntimeHandle handle;

    @BeforeEach
    void configureTimeouts() {
        when(runtimeProperties.serviceCallTimeout()).thenReturn(Duration.ofSeconds(10));
        when(runtimeProperties.maxServiceCallTimeout()).thenReturn(Duration.ofSeconds(30));
    }

    @Test
    void callsAnExplicitlyAuthorizedTargetInstallation() {
        arrangeCallerAndTarget();
        when(permissionStore.isAllowed(CALLER_INSTALLATION, TARGET_INSTALLATION, OPERATION)).thenReturn(true);
        when(runtimeService.ensureRunningHandle(TARGET_INSTALLATION, "plugin:" + CALLER_INSTALLATION)).thenReturn(handle);
        when(handle.instanceId()).thenReturn("target-runner");
        when(handle.logPath()).thenReturn(Path.of("target-runner.log"));
        when(taskInvoker.callService(eq(handle), any(PluginServiceRunRequest.class), eq(Duration.ofSeconds(12))))
                .thenReturn(PluginServiceRunResponse.newBuilder()
                        .setSuccess(true)
                        .setResultJson("{\"ok\":true}")
                        .setMessage("done")
                        .build());

        var response = coordinator().call(principal(), request(TARGET_INSTALLATION, TARGET_PLUGIN, "42", 12_000));

        assertThat(response.getSuccess()).isTrue();
        assertThat(response.getResultJson()).isEqualTo("{\"ok\":true}");
        ArgumentCaptor<PluginServiceRunRequest> forwarded = ArgumentCaptor.forClass(PluginServiceRunRequest.class);
        verify(taskInvoker).callService(eq(handle), forwarded.capture(), eq(Duration.ofSeconds(12)));
        assertThat(forwarded.getValue().getCallerInstallationId()).isEqualTo(CALLER_INSTALLATION);
        assertThat(forwarded.getValue().getTargetInstallationId()).isEqualTo(TARGET_INSTALLATION);
        assertThat(forwarded.getValue().getTargetPluginId()).isEqualTo(TARGET_PLUGIN);
        assertThat(forwarded.getValue().getRequestJson()).isEqualTo("{\"value\":1}");
        verify(permissionStore, never()).authorizedTargetInstallationIds(any(), any(), any());
    }

    @Test
    void legacyPluginIdCallWorksOnlyWhenOneAuthorizedInstallationMatches() {
        arrangeCallerAndTarget();
        when(permissionStore.authorizedTargetInstallationIds(CALLER_INSTALLATION, TARGET_PLUGIN, OPERATION))
                .thenReturn(List.of(TARGET_INSTALLATION));
        when(permissionStore.isAllowed(CALLER_INSTALLATION, TARGET_INSTALLATION, OPERATION)).thenReturn(true);
        when(runtimeService.ensureRunningHandle(TARGET_INSTALLATION, "plugin:" + CALLER_INSTALLATION)).thenReturn(handle);
        when(handle.instanceId()).thenReturn("target-runner");
        when(handle.logPath()).thenReturn(Path.of("target-runner.log"));
        when(taskInvoker.callService(eq(handle), any(PluginServiceRunRequest.class), any(Duration.class)))
                .thenReturn(PluginServiceRunResponse.newBuilder().setSuccess(true).setResultJson("{}").build());

        assertThat(coordinator().call(principal(), request("", TARGET_PLUGIN, "42", 0)).getSuccess()).isTrue();

        verify(permissionStore).authorizedTargetInstallationIds(CALLER_INSTALLATION, TARGET_PLUGIN, OPERATION);
        verify(runtimeStore).findRuntimeByInstallationId(TARGET_INSTALLATION);
    }

    @Test
    void legacyPluginIdCallRejectsAmbiguousAuthorizedInstallations() {
        arrangeCaller();
        when(permissionStore.authorizedTargetInstallationIds(CALLER_INSTALLATION, TARGET_PLUGIN, OPERATION))
                .thenReturn(List.of("target-a", "target-b"));

        assertStatus(
                () -> coordinator().call(principal(), request("", TARGET_PLUGIN, "42", 0)),
                Status.Code.FAILED_PRECONDITION,
                "targetInstallationId is required"
        );

        verify(runtimeStore, never()).findRuntimeByInstallationId("target-a");
        verify(runtimeService, never()).ensureRunningHandle(any(), any());
    }

    @Test
    void forgedJobContextIsRejectedBeforeRuntimeLookup() {
        assertStatus(
                () -> coordinator().call(principal(), request(TARGET_INSTALLATION, TARGET_PLUGIN, "999", 0)),
                Status.Code.PERMISSION_DENIED,
                "Job context mismatch"
        );

        verify(runtimeStore, never()).findRuntimeByInstallationId(any());
        verify(permissionStore, never()).isAllowed(any(), any(), any());
    }

    @Test
    void targetInstallationCannotBeUsedWithoutItsPermissionEdge() {
        arrangeCaller();
        when(permissionStore.isAllowed(CALLER_INSTALLATION, TARGET_INSTALLATION, OPERATION)).thenReturn(false);

        assertStatus(
                () -> coordinator().call(principal(), request(TARGET_INSTALLATION, TARGET_PLUGIN, "42", 0)),
                Status.Code.PERMISSION_DENIED,
                "not allowed"
        );

        verify(runtimeStore, never()).findRuntimeByInstallationId(TARGET_INSTALLATION);
        verify(runtimeService, never()).ensureRunningHandle(any(), any());
    }

    @Test
    void targetInstallationAndPluginIdentityMustAgree() {
        arrangeCaller();
        when(runtimeStore.findRuntimeByInstallationId(TARGET_INSTALLATION)).thenReturn(Optional.of(target()));
        when(permissionStore.isAllowed(CALLER_INSTALLATION, TARGET_INSTALLATION, OPERATION)).thenReturn(true);

        assertStatus(
                () -> coordinator().call(principal(), request(TARGET_INSTALLATION, "forged-plugin", "42", 0)),
                Status.Code.PERMISSION_DENIED,
                "Target plugin identity mismatch"
        );

        verify(permissionStore).isAllowed(CALLER_INSTALLATION, TARGET_INSTALLATION, OPERATION);
    }

    @Test
    void rejectsCallerWhenTokenInstanceDoesNotMatchExecutionInstance() {
        arrangeCaller();
        when(executionStore.findByIdUnscoped(420L)).thenReturn(Optional.of(execution("forged-instance")));

        assertStatus(
                () -> coordinator().call(principal(), request(TARGET_INSTALLATION, TARGET_PLUGIN, "42", 0)),
                Status.Code.PERMISSION_DENIED,
                "Caller plugin runtime identity mismatch"
        );

        verify(permissionStore, never()).isAllowed(any(), any(), any());
    }

    private void arrangeCallerAndTarget() {
        arrangeCaller();
        when(runtimeStore.findRuntimeByInstallationId(TARGET_INSTALLATION)).thenReturn(Optional.of(target()));
        when(runtimeStore.runtimeCapabilities("{\"service\":true}")).thenReturn(Set.of(PluginCapability.SERVICE));
    }

    private void arrangeCaller() {
        when(runtimeStore.findRuntimeByInstallationId(CALLER_INSTALLATION)).thenReturn(Optional.of(caller()));
        when(executionStore.findByIdUnscoped(420L)).thenReturn(Optional.of(execution(CALLER_INSTANCE)));
    }

    private PluginServiceCallCoordinator coordinator() {
        return new PluginServiceCallCoordinator(
                runtimeStore,
                executionStore,
                runtimeService,
                taskInvoker,
                permissionStore,
                idleTracker,
                callLogStore,
                auditStore,
                new ObjectMapper(),
                runtimeProperties
        );
    }

    private PluginRuntimePrincipal principal() {
        return new PluginRuntimePrincipal(
                CALLER_INSTALLATION,
                "caller-plugin",
                CALLER_INSTANCE,
                42L,
                420L,
                Set.of(PluginCapability.TASK),
                Instant.now(),
                Instant.now().plusSeconds(60)
        );
    }

    private PluginServiceCallRequest request(String targetInstallationId, String targetPluginId, String jobId, long timeoutMillis) {
        return PluginServiceCallRequest.newBuilder()
                .setCallerPluginId("caller-plugin")
                .setTargetInstallationId(targetInstallationId)
                .setTargetPluginId(targetPluginId)
                .setOperation(OPERATION)
                .setRequestJson(" { \"value\" : 1 } ")
                .setJobId(jobId)
                .setRequestId("request-1")
                .setTimeoutMillis(timeoutMillis)
                .build();
    }

    private PluginRuntimeRecord caller() {
        return runtime(CALLER_INSTALLATION, "caller-plugin", "caller-instance", "{\"task\":true}", "owner-a", 10L);
    }

    private PluginRuntimeRecord target() {
        return runtime(TARGET_INSTALLATION, TARGET_PLUGIN, "target-instance", "{\"service\":true}", "owner-b", 20L);
    }

    private JobExecution execution(String instanceId) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new JobExecution(
                420L, 42L, CALLER_INSTALLATION, "caller-plugin", instanceId, "request-1",
                JobExecutionStatus.RUNNING, JobTriggerType.MANUAL, "job", now, null, null,
                "", "{}", "", "", "runner.log", "owner-a", 10L, now
        );
    }

    private PluginRuntimeRecord runtime(
            String installationId,
            String pluginId,
            String instanceId,
            String capabilitiesJson,
            String ownerUserId,
            Long ownerDepartmentId
    ) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new PluginRuntimeRecord(
                1L, installationId, pluginId, instanceId, pluginId + " installation", "1.0.0", pluginId,
                "demo.Plugin", "plugin.jar", "sha", "process", PluginRuntimeState.STOPPED,
                "127.0.0.1", null, "", capabilitiesJson, "{}", "{}", "[]",
                false, 5005, true, ownerUserId, ownerUserId, ownerDepartmentId, "Department", null, now, now
        );
    }

    private void assertStatus(Runnable action, Status.Code code, String description) {
        assertThatThrownBy(action::run)
                .isInstanceOfSatisfying(StatusRuntimeException.class, exception -> {
                    assertThat(exception.getStatus().getCode()).isEqualTo(code);
                    assertThat(exception.getStatus().getDescription()).contains(description);
                });
    }
}
