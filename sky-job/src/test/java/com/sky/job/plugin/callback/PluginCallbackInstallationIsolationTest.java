package com.sky.job.plugin.callback;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.NotificationService;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginDataStore;
import com.sky.job.plugin.storage.PluginLogStore;
import com.sky.job.plugin.storage.PluginPayloadValidator;
import com.sky.job.system.DynamicNotificationSettings;
import com.sky.plugin.protocol.AccountRequest;
import com.sky.plugin.protocol.ConfigRequest;
import com.sky.plugin.protocol.PluginDataRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginCallbackInstallationIsolationTest {
    @Mock private PluginConfigStore configStore;
    @Mock private PluginAccountStore accountStore;
    @Mock private PluginDataStore dataStore;
    @Mock private PluginPayloadValidator payloadValidator;
    @Mock private PluginLogStore logStore;
    @Mock private NotificationService notificationService;
    @Mock private DynamicNotificationSettings notificationSettings;
    @Mock private PluginServiceCallCoordinator serviceCallCoordinator;

    @Test
    void samePluginDefinitionUsesTokenBoundInstallationForConfigAccountsAndData() {
        when(configStore.getConfigJson("installation-a")).thenReturn("{\"tenant\":\"a\"}");
        when(configStore.getConfigJson("installation-b")).thenReturn("{\"tenant\":\"b\"}");
        when(accountStore.listEnabled("installation-a")).thenReturn(List.of());
        when(accountStore.listEnabled("installation-b")).thenReturn(List.of());
        when(dataStore.select("installation-a", "state", "key")).thenReturn(Optional.of("{\"value\":\"a\"}"));
        when(dataStore.select("installation-b", "state", "key")).thenReturn(Optional.of("{\"value\":\"b\"}"));

        PluginCallbackService service = service();
        ConfigRequest configRequest = ConfigRequest.newBuilder().setPluginId("demo").build();
        AccountRequest accountRequest = AccountRequest.newBuilder().setPluginId("demo").build();
        PluginDataRequest dataRequest = PluginDataRequest.newBuilder()
                .setPluginId("demo").setCollection("state").setKey("key").build();

        assertThat(service.getConfig(principal("installation-a", "runner-a"), configRequest).getConfigJson())
                .isEqualTo("{\"tenant\":\"a\"}");
        assertThat(service.getConfig(principal("installation-b", "runner-b"), configRequest).getConfigJson())
                .isEqualTo("{\"tenant\":\"b\"}");
        service.listAccounts(principal("installation-a", "runner-a"), accountRequest);
        service.listAccounts(principal("installation-b", "runner-b"), accountRequest);
        assertThat(service.getData(principal("installation-a", "runner-a"), dataRequest).getDataJson())
                .isEqualTo("{\"value\":\"a\"}");
        assertThat(service.getData(principal("installation-b", "runner-b"), dataRequest).getDataJson())
                .isEqualTo("{\"value\":\"b\"}");

        verify(configStore).getConfigJson("installation-a");
        verify(configStore).getConfigJson("installation-b");
        verify(accountStore).listEnabled("installation-a");
        verify(accountStore).listEnabled("installation-b");
        verify(dataStore).select("installation-a", "state", "key");
        verify(dataStore).select("installation-b", "state", "key");
    }

    private PluginCallbackService service() {
        return new PluginCallbackService(
                configStore,
                accountStore,
                dataStore,
                payloadValidator,
                logStore,
                new ObjectMapper(),
                notificationService,
                notificationSettings,
                serviceCallCoordinator
        );
    }

    private PluginRuntimePrincipal principal(String installationId, String instanceId) {
        return new PluginRuntimePrincipal(
                installationId,
                "demo",
                instanceId,
                42L,
                420L,
                Set.of(PluginCapability.TASK, PluginCapability.CONFIG, PluginCapability.ACCOUNT),
                Instant.now(),
                Instant.now().plusSeconds(60)
        );
    }
}
