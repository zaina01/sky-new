package com.sky.job.plugin.callback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.NotificationSendReceipt;
import com.sky.job.notification.NotificationSendStatus;
import com.sky.job.notification.NotificationService;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginDataStore;
import com.sky.job.plugin.storage.PluginLogStore;
import com.sky.job.plugin.storage.PluginPayloadValidator;
import com.sky.job.system.DynamicNotificationSettings;
import com.sky.plugin.protocol.NotificationRequest;
import com.sky.plugin.protocol.NotificationResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PluginCallbackNotificationLoggingTest {
    @Mock private PluginConfigStore configStore;
    @Mock private PluginAccountStore accountStore;
    @Mock private PluginDataStore dataStore;
    @Mock private PluginPayloadValidator payloadValidator;
    @Mock private PluginLogStore logStore;
    @Mock private NotificationService notificationService;
    @Mock private DynamicNotificationSettings notificationSettings;
    @Mock private PluginServiceCallCoordinator serviceCallCoordinator;

    private ch.qos.logback.classic.Logger callbackLogger;
    private ListAppender<ILoggingEvent> logAppender;

    @BeforeEach
    void captureCallbackLogs() {
        callbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(PluginCallbackService.class);
        logAppender = new ListAppender<>();
        logAppender.start();
        callbackLogger.addAppender(logAppender);
    }

    @AfterEach
    void stopCapturingCallbackLogs() {
        callbackLogger.detachAppender(logAppender);
        logAppender.stop();
    }

    @Test
    void oversizedContentKeepsResponseAndLogsLimitsWithoutPayload() {
        String content = "TOP-SECRET-CONTENT-" + "中".repeat(3_000);
        when(notificationSettings.maxTitleBytes()).thenReturn(512);
        when(notificationSettings.maxContentBytes()).thenReturn(8_192);
        when(notificationService.sendFromPlugin(any(), any()))
                .thenThrow(new IllegalArgumentException("Notification content is too large"));

        NotificationResponse response = service().sendNotification(principal(), request(content));

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Notification content is too large");
        assertThat(logAppender.list).singleElement().satisfies(event -> {
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(event.getFormattedMessage())
                    .contains("installationId=installation-a")
                    .contains("pluginId=demo")
                    .contains("instanceId=runner-a")
                    .contains("jobId=42")
                    .contains("executionId=420")
                    .contains("requestId=request-a")
                    .contains("channelIds=[channel-a]")
                    .contains("maxTitleBytes=512")
                    .contains("contentBytes=" + content.getBytes(StandardCharsets.UTF_8).length)
                    .contains("maxContentBytes=8192")
                    .contains("reason=Notification content is too large")
                    .doesNotContain("TOP-SECRET-CONTENT");
        });
    }

    @Test
    void skippedAndFailedReceiptsAreCombinedIntoOneSingleLineWarning() {
        String longReason = "provider refused\n" + "x".repeat(600);
        when(notificationSettings.maxTitleBytes()).thenReturn(512);
        when(notificationSettings.maxContentBytes()).thenReturn(8_192);
        when(notificationService.sendFromPlugin(any(), any())).thenReturn(List.of(
                new NotificationSendReceipt("channel-disabled", NotificationSendStatus.SKIPPED, "Notification channel is disabled", 10L),
                new NotificationSendReceipt("channel-failed", NotificationSendStatus.FAILED, longReason, 11L)
        ));

        NotificationResponse response = service().sendNotification(principal(), request("safe content"));

        assertThat(response.getSuccess()).isFalse();
        assertThat(response.getMessage()).isEqualTo("Notification queued");
        assertThat(logAppender.list).singleElement().satisfies(event -> {
            String message = event.getFormattedMessage();
            assertThat(event.getLevel()).isEqualTo(Level.WARN);
            assertThat(message)
                    .contains("reason=Notification queued")
                    .contains("channel-disabled:SKIPPED(message=Notification channel is disabled, historyId=10)")
                    .contains("channel-failed:FAILED(message=provider refused ")
                    .contains("...[truncated]")
                    .doesNotContain("\n");
            assertThat(message.length()).isLessThan(4_000);
        });
    }

    @Test
    void successfullyQueuedNotificationDoesNotLogARejection() {
        when(notificationService.sendFromPlugin(any(), any())).thenReturn(List.of(
                new NotificationSendReceipt("channel-a", NotificationSendStatus.PENDING, "Notification queued", 12L)
        ));

        NotificationResponse response = service().sendNotification(principal(), request("safe content"));

        assertThat(response.getSuccess()).isTrue();
        assertThat(logAppender.list).isEmpty();
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

    private PluginRuntimePrincipal principal() {
        return new PluginRuntimePrincipal(
                "installation-a",
                "demo",
                "runner-a",
                42L,
                420L,
                Set.of(PluginCapability.TASK, PluginCapability.NOTIFICATION),
                Instant.now(),
                Instant.now().plusSeconds(60)
        );
    }

    private NotificationRequest request(String content) {
        return NotificationRequest.newBuilder()
                .setPluginId("demo")
                .setInstanceId("runner-a")
                .setJobId("42")
                .setRequestId("request-a")
                .addChannelIds("channel-a")
                .setTitle("标题")
                .setContent(content)
                .setSeverity("INFO")
                .build();
    }
}
