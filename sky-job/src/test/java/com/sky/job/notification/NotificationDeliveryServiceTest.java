package com.sky.job.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.provider.NotificationProvider;
import com.sky.job.notification.provider.NotificationProviderRegistry;
import com.sky.job.system.DynamicNotificationSettings;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryServiceTest {

    @Test
    void failedChunkRetriesWithoutResendingSuccessfulChunk() {
        DynamicNotificationSettings properties = mock(DynamicNotificationSettings.class);
        NotificationSendHistoryStore historyStore = mock(NotificationSendHistoryStore.class);
        NotificationChannelStore channelStore = mock(NotificationChannelStore.class);
        NotificationProviderRegistry providerRegistry = mock(NotificationProviderRegistry.class);
        NotificationProvider provider = mock(NotificationProvider.class);
        ObjectMapper objectMapper = new ObjectMapper();
        NotificationPayloadValidator validator = new NotificationPayloadValidator(
                properties, new NotificationSecretMasker(objectMapper));
        NotificationSendHistory first = history(1L, "Title [1/2]", "first");
        NotificationSendHistory second = history(2L, "Title [2/2]", "second");
        NotificationChannel channel = channel();

        when(properties.retryBatchSize()).thenReturn(20);
        when(properties.retryStaleTimeout()).thenReturn(Duration.ofMinutes(5));
        when(properties.retryInitialDelay()).thenReturn(Duration.ofSeconds(30));
        when(properties.retryMaxDelay()).thenReturn(Duration.ofMinutes(10));
        when(historyStore.claimDue(anyInt(), anyString(), any(LocalDateTime.class), any(Duration.class)))
                .thenReturn(List.of(first, second), List.of(second));
        when(channelStore.findByChannelIdUnscoped("webhook")).thenReturn(Optional.of(channel));
        when(providerRegistry.get(NotificationChannelType.WEBHOOK)).thenReturn(provider);
        when(provider.send(eq(channel), any()))
                .thenReturn(
                        NotificationSendResult.success("200", "ok"),
                        NotificationSendResult.failed("WEBHOOK_REJECTED", "rate limited", "200", "failed"),
                        NotificationSendResult.success("200", "ok")
                );

        NotificationDeliveryService service = new NotificationDeliveryService(
                properties, historyStore, channelStore, providerRegistry, validator, objectMapper);

        service.runScheduled();
        service.runScheduled();

        ArgumentCaptor<NotificationSendCommand> commands = ArgumentCaptor.forClass(NotificationSendCommand.class);
        verify(provider, times(3)).send(eq(channel), commands.capture());
        assertThat(commands.getAllValues()).extracting(NotificationSendCommand::content)
                .containsExactly("first", "second", "second");
        verify(historyStore).markSendingSuccess(eq(1L), any());
        verify(historyStore).markSendingRetry(eq(2L), any(), any(LocalDateTime.class));
        verify(historyStore).markSendingSuccess(eq(2L), any());
        verify(historyStore, never()).markSendingRetry(eq(1L), any(), any(LocalDateTime.class));
    }

    private NotificationSendHistory history(long id, String title, String content) {
        NotificationSendHistory history = mock(NotificationSendHistory.class);
        when(history.id()).thenReturn(id);
        when(history.sourceType()).thenReturn(NotificationSourceType.PLUGIN_RUNTIME);
        when(history.requestId()).thenReturn("request-1");
        when(history.installationId()).thenReturn("installation-1");
        when(history.pluginId()).thenReturn("plugin-1");
        when(history.instanceId()).thenReturn("instance-1");
        when(history.jobId()).thenReturn("job-1");
        when(history.executionId()).thenReturn(1L);
        when(history.sourceOwnerUserId()).thenReturn("owner");
        when(history.sourceOwnerDepartmentId()).thenReturn(1L);
        when(history.channelId()).thenReturn("webhook");
        when(history.title()).thenReturn(title);
        when(history.contentPayload()).thenReturn(content);
        when(history.severity()).thenReturn("INFO");
        when(history.labelsJson()).thenReturn("{}");
        when(history.attemptCount()).thenReturn(0);
        when(history.maxAttempts()).thenReturn(3);
        return history;
    }

    private NotificationChannel channel() {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 10, 0);
        return new NotificationChannel(
                1L, "webhook", NotificationChannelType.WEBHOOK, "Webhook", true,
                "{\"url\":\"https://example.test/hook\"}", "", "owner", "owner", 1L,
                "Department", "owner", "owner", now, now);
    }
}
