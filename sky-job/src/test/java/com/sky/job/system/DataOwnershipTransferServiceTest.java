package com.sky.job.system;

import com.sky.job.audit.SystemLogStore;
import com.sky.job.notification.NotificationChannelStore;
import com.sky.job.notification.NotificationRuleStore;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.scheduler.ExecutedStatus;
import com.sky.job.scheduler.JobStatus;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.MisfirePolicy;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.ScheduledJob;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.DataScopeType;
import com.sky.job.security.PermissionNames;
import com.sky.job.security.RoleDataScopeGrant;
import com.sky.job.system.dto.DataOwnershipTransferRequest;
import com.sky.job.system.mapper.DataOwnershipMapper;
import com.sky.job.system.mapper.DepartmentMapper;
import com.sky.job.system.mapper.RoleMapper;
import com.sky.job.user.User;
import com.sky.job.user.UserStore;
import com.sky.job.web.ResourceNotFoundException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DataOwnershipTransferServiceTest {
    private static final String INSTALLATION_ID = "installation-a";
    private static final long JOB_ID = 42L;

    @Mock private UserStore userStore;
    @Mock private PluginRuntimeMetadataStore runtimeStore;
    @Mock private JobStore jobStore;
    @Mock private NotificationChannelStore channelStore;
    @Mock private NotificationRuleStore ruleStore;
    @Mock private DataOwnershipMapper ownershipMapper;
    @Mock private RoleMapper roleMapper;
    @Mock private DepartmentMapper departmentMapper;
    @Mock private SystemLogStore systemLogStore;
    @Mock private HttpServletRequest httpRequest;

    @Test
    void transfersLinkedResourcesAtomicallyWhenTargetCanSeeThePostTransferGraph() {
        arrangeTargetAndResources();
        arrangeNoExtraLinks();
        when(roleMapper.dataScopeGrants("actor", PermissionNames.SYSTEM_DATA_OWNERSHIP_TRANSFER))
                .thenReturn(List.of(grant(1L, DataScopeType.ALL)));
        when(roleMapper.dataScopeGrants(eq("target"), anyString()))
                .thenReturn(List.of(grant(2L, DataScopeType.SELF)));
        when(runtimeStore.transferOwnership(INSTALLATION_ID, "target", 10L)).thenReturn(1);
        when(jobStore.transferOwnership(JOB_ID, "target", 10L, "operator")).thenReturn(1);

        var response = service().transfer(
                request(List.of(INSTALLATION_ID), List.of(JOB_ID)),
                actor(),
                httpRequest
        );

        assertThat(response.targetUserId()).isEqualTo("target");
        assertThat(response.targetDepartmentId()).isEqualTo(10L);
        assertThat(response.pluginInstallationsTransferred()).isEqualTo(1);
        assertThat(response.jobsTransferred()).isEqualTo(1);
        verify(runtimeStore).transferOwnership(INSTALLATION_ID, "target", 10L);
        verify(jobStore).transferOwnership(JOB_ID, "target", 10L, "operator");
        verify(channelStore, never()).transferOwnership(anyString(), anyString(), eq(10L), anyString());
        verify(ruleStore, never()).transferOwnership(anyString(), anyString(), eq(10L), anyString());
        verify(systemLogStore).record(
                eq("DATA_OWNERSHIP"), eq("TRANSFER"), eq("INFO"), eq("SUCCESS"),
                eq("USER"), eq("target"), anyString(), eq(httpRequest));
    }

    @Test
    void rejectsPartialTransferWhenLinkedInstallationWouldBeInvisibleToTarget() {
        arrangeTargetAndResources();
        when(ownershipMapper.ruleIdsByJobId(JOB_ID)).thenReturn(List.of());
        when(jobStore.notificationChannelIds(JOB_ID)).thenReturn(List.of());
        when(roleMapper.dataScopeGrants("actor", PermissionNames.SYSTEM_DATA_OWNERSHIP_TRANSFER))
                .thenReturn(List.of(grant(1L, DataScopeType.ALL)));
        when(roleMapper.dataScopeGrants(eq("target"), anyString()))
                .thenReturn(List.of(grant(2L, DataScopeType.SELF)));

        assertThatThrownBy(() -> service().transfer(
                request(List.of(), List.of(JOB_ID)),
                actor(),
                httpRequest
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Target user cannot access linked plugin installation")
                .hasMessageContaining(INSTALLATION_ID);

        verify(runtimeStore, never()).transferOwnership(anyString(), anyString(), eq(10L));
        verify(jobStore, never()).transferOwnership(eq(JOB_ID), anyString(), eq(10L), anyString());
    }

    @Test
    void selectedResourceOutsideActorScopeIsReportedAsNotFound() {
        when(userStore.findByUserId("target")).thenReturn(Optional.of(targetUser()));
        when(jobStore.findByIdUnscoped(JOB_ID)).thenReturn(Optional.of(job()));
        when(roleMapper.dataScopeGrants("actor", PermissionNames.SYSTEM_DATA_OWNERSHIP_TRANSFER))
                .thenReturn(List.of());

        assertThatThrownBy(() -> service().transfer(
                request(List.of(), List.of(JOB_ID)),
                actor(),
                httpRequest
        ))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Job not found");

        verify(jobStore, never()).transferOwnership(eq(JOB_ID), anyString(), eq(10L), anyString());
    }

    private DataOwnershipTransferService service() {
        return new DataOwnershipTransferService(
                userStore,
                runtimeStore,
                jobStore,
                channelStore,
                ruleStore,
                ownershipMapper,
                new DataScopeService(roleMapper, departmentMapper),
                systemLogStore
        );
    }

    private void arrangeTargetAndResources() {
        when(userStore.findByUserId("target")).thenReturn(Optional.of(targetUser()));
        when(userStore.roles("target")).thenReturn(Set.of("TARGET_ROLE"));
        when(userStore.permissions("target")).thenReturn(Set.of(
                PermissionNames.PLUGIN_RUNTIME_READ,
                PermissionNames.JOB_READ,
                PermissionNames.NOTIFICATION_CHANNEL_READ,
                PermissionNames.NOTIFICATION_RULE_READ
        ));
        when(runtimeStore.findRuntimeByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(runtime()));
        when(jobStore.findByIdUnscoped(JOB_ID)).thenReturn(Optional.of(job()));
    }

    private void arrangeNoExtraLinks() {
        when(ownershipMapper.jobIdsByInstallationId(INSTALLATION_ID)).thenReturn(List.of(JOB_ID));
        when(ownershipMapper.ruleIdsByInstallationId(INSTALLATION_ID)).thenReturn(List.of());
        when(ownershipMapper.servicePeerInstallationIds(INSTALLATION_ID)).thenReturn(List.of());
        when(ownershipMapper.ruleIdsByJobId(JOB_ID)).thenReturn(List.of());
        when(runtimeStore.notificationChannelIds(INSTALLATION_ID)).thenReturn(List.of());
        when(jobStore.notificationChannelIds(JOB_ID)).thenReturn(List.of());
    }

    private DataOwnershipTransferRequest request(List<String> installationIds, List<Long> jobIds) {
        return new DataOwnershipTransferRequest("target", 10L, installationIds, jobIds, List.of(), List.of());
    }

    private AuthenticatedUser actor() {
        return new AuthenticatedUser(
                "actor", "operator", "", 99L, true,
                Set.of("TRANSFER_ROLE"), Set.of(PermissionNames.SYSTEM_DATA_OWNERSHIP_TRANSFER)
        );
    }

    private User targetUser() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new User(
                "target", "target-user", "", "target@example.com", null, null,
                10L, "Target Department", true, true, now, now
        );
    }

    private PluginRuntimeRecord runtime() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new PluginRuntimeRecord(
                1L, INSTALLATION_ID, "demo", "demo-instance", "Demo", "1.0.0", "Demo",
                "demo.Plugin", "plugin.jar", "sha", "process", PluginRuntimeState.STOPPED,
                "127.0.0.1", null, "", "{\"task\":true}", "{}", "{}", "[]",
                false, 5005, true, "source-owner", "source", 20L, "Source Department", null, now, now
        );
    }

    private ScheduledJob job() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 2, 10, 0);
        return new ScheduledJob(
                JOB_ID, "job", INSTALLATION_ID, "demo", "Demo", "0 * * * * ?",
                JobStatus.ENABLED, MisfirePolicy.DO_NOTHING, false, "{}", 10_000,
                ExecutedStatus.IDLE, "", "source-owner", "source", 20L, "Source Department",
                "source", "source", now, now
        );
    }

    private RoleDataScopeGrant grant(long roleId, DataScopeType type) {
        return new RoleDataScopeGrant(roleId, "role-" + roleId, type);
    }
}
