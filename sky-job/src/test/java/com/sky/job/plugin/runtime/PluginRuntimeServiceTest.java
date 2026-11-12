package com.sky.job.plugin.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.NotificationChannelStore;
import com.sky.job.notification.NotificationService;
import com.sky.job.plugin.artifact.PluginArtifactRegistry;
import com.sky.job.plugin.runtime.dto.PluginRuntimeResponse;
import com.sky.job.plugin.runtime.dto.ReplaceNotificationChannelsRequest;
import com.sky.job.plugin.runtime.dto.UpdatePluginRuntimeDebugRequest;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginAccountGroupStore;
import com.sky.job.plugin.storage.PluginAccountListPreferenceStore;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginDataStore;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import com.sky.job.web.ResourceNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginRuntimeServiceTest {
    private static final String INSTALLATION_ID = "installation-a";

    @Mock private PluginRuntimeMetadataStore store;
    @Mock private PluginArtifactRegistry artifactRegistry;
    @Mock private DataScopeService dataScopeService;
    @Mock private ManagedPluginRuntimeHandleRegistry registry;
    @Mock private PluginRuntimeAdapter runtimeAdapter;
    @Mock private ProcessPluginRuntimeProperties runtimeProperties;
    @Mock private DynamicProcessPluginRuntimeSettings runtimeSettings;
    @Mock private JobStore jobStore;
    @Mock private PluginConfigStore configStore;
    @Mock private PluginAccountStore accountStore;
    @Mock private PluginAccountGroupStore accountGroupStore;
    @Mock private PluginAccountListPreferenceStore accountListPreferenceStore;
    @Mock private PluginDataStore dataStore;
    @Mock private PluginAuditStore auditStore;
    @Mock private NotificationService notificationService;
    @Mock private NotificationChannelStore notificationChannelStore;

    @Test
    void updatesRemoteDebugSettingsForConcreteInstallation() {
        PluginRuntimeRecord before = runtime(false, 5005, true);
        PluginRuntimeRecord after = runtime(true, 5010, false);
        when(store.findRuntimeByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(before), Optional.of(after));
        when(store.updateRemoteDebug(INSTALLATION_ID, true, 5010, false)).thenReturn(1);
        when(registry.get(INSTALLATION_ID)).thenReturn(Optional.empty());

        PluginRuntimeResponse response = service().updateDebug(
                INSTALLATION_ID,
                new UpdatePluginRuntimeDebugRequest(true, 5010, false),
                admin()
        );

        assertThat(response.installationId()).isEqualTo(INSTALLATION_ID);
        assertThat(response.remoteDebugEnabled()).isTrue();
        assertThat(response.remoteDebugPort()).isEqualTo(5010);
        assertThat(response.remoteDebugSuspend()).isFalse();
        verify(store).updateRemoteDebug(INSTALLATION_ID, true, 5010, false);
        verify(auditStore).insert(
                eq(INSTALLATION_ID), eq("demo"), eq("demo-instance"), eq("alice"),
                eq("PLUGIN_RUNTIME_DEBUG_UPDATE"), anyString());
    }

    @Test
    void rejectsRemoteDebugPortOutsideValidRange() {
        when(store.findRuntimeByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(runtime(false, 5005, true)));

        assertThatThrownBy(() -> service().updateDebug(
                INSTALLATION_ID,
                new UpdatePluginRuntimeDebugRequest(true, 70000, true),
                admin()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Plugin runtime debug port is out of range");

        verify(store, never()).updateRemoteDebug(anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyBoolean());
    }

    @Test
    void returnsNotFoundForUnknownInstallation() {
        when(store.findRuntimeByInstallationId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().updateDebug(
                "missing",
                new UpdatePluginRuntimeDebugRequest(true, 5005, true),
                admin()))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessage("Plugin installation not found");
    }

    @Test
    void readsInstallationNotificationChannelPoolForSafeEditing() {
        when(store.findRuntimeByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(runtime(false, 5005, true)));
        when(store.notificationChannelIds(INSTALLATION_ID)).thenReturn(List.of("channel-a", "channel-b"));

        assertThat(service().notificationChannelIds(INSTALLATION_ID, admin()))
                .containsExactly("channel-a", "channel-b");

        verify(store).notificationChannelIds(INSTALLATION_ID);
    }

    @Test
    void refusesToShrinkInstallationChannelPoolWhileJobsStillUseRemovedChannels() {
        when(store.findRuntimeByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(runtime(false, 5005, true)));
        when(store.jobNotificationChannelIds(INSTALLATION_ID)).thenReturn(List.of("job-channel"));

        assertThatThrownBy(() -> service().replaceNotificationChannels(
                INSTALLATION_ID,
                new ReplaceNotificationChannelsRequest(List.of()),
                admin()
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("still bound to jobs")
                .hasMessageContaining("job-channel");

        verify(store, never()).replaceNotificationChannels(eq(INSTALLATION_ID), any(), anyString());
    }

    @Test
    void removesAllAccountListPreferencesWhenInstallationIsUninstalled() {
        when(store.findRuntimeByInstallationId(INSTALLATION_ID)).thenReturn(Optional.of(runtime(false, 5005, true)));
        when(jobStore.existsByPluginInstallationId(INSTALLATION_ID)).thenReturn(false);
        when(registry.remove(INSTALLATION_ID)).thenReturn(Optional.empty());

        service().uninstall(INSTALLATION_ID, admin());

        verify(accountListPreferenceStore).deleteByInstallationId(INSTALLATION_ID);
        org.mockito.InOrder cleanup = org.mockito.Mockito.inOrder(accountStore, accountGroupStore, store);
        cleanup.verify(accountStore).deleteByInstallationId(INSTALLATION_ID);
        cleanup.verify(accountGroupStore).deleteByInstallationId(INSTALLATION_ID);
        verify(store).softDelete(INSTALLATION_ID);
    }

    private PluginRuntimeService service() {
        EffectiveDataScope all = EffectiveDataScope.all("user-1");
        org.mockito.Mockito.lenient().when(dataScopeService.resolve(any(), anyString())).thenReturn(all);
        org.mockito.Mockito.lenient().when(dataScopeService.allows(eq(all), anyString(), any())).thenReturn(true);
        return new PluginRuntimeService(
                store,
                artifactRegistry,
                dataScopeService,
                registry,
                runtimeAdapter,
                runtimeProperties,
                runtimeSettings,
                jobStore,
                configStore,
                accountStore,
                accountGroupStore,
                accountListPreferenceStore,
                dataStore,
                auditStore,
                new ObjectMapper(),
                notificationService,
                notificationChannelStore,
                19090
        );
    }

    private AuthenticatedUser admin() {
        return new AuthenticatedUser("user-1", "alice", "", 1L, true, Set.of("ADMIN"), Set.of());
    }

    private PluginRuntimeRecord runtime(boolean remoteDebugEnabled, int remoteDebugPort, boolean remoteDebugSuspend) {
        LocalDateTime now = LocalDateTime.of(2026, 7, 31, 10, 0);
        return new PluginRuntimeRecord(
                1L,
                INSTALLATION_ID,
                "demo",
                "demo-instance",
                "Demo Default",
                "1.0.0",
                "Demo",
                "demo.Plugin",
                "plugin.jar",
                "sha",
                "process",
                PluginRuntimeState.STOPPED,
                "127.0.0.1",
                null,
                "",
                "{\"task\":true}",
                "{}",
                "{}",
                "[]",
                remoteDebugEnabled,
                remoteDebugPort,
                remoteDebugSuspend,
                "user-1",
                "alice",
                1L,
                "Default",
                null,
                now,
                now
        );
    }
}
