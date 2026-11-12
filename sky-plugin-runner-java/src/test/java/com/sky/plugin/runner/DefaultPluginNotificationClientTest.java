package com.sky.plugin.runner;

import com.sky.plugin.api.NotificationSendRequest;
import com.sky.plugin.api.NotificationSendResult;
import com.sky.plugin.protocol.NotificationChannelListRequest;
import com.sky.plugin.protocol.NotificationChannelListResponse;
import com.sky.plugin.protocol.NotificationRequest;
import com.sky.plugin.protocol.NotificationResponse;
import com.sky.plugin.protocol.NotificationSendReceipt;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultPluginNotificationClientTest {
    @Test
    void rejectedResponseIsReturnedAndReportedWithoutPayload() {
        String content = "TOP-SECRET-CONTENT-" + "中".repeat(3_000);
        FakeNotificationHostGateway gateway = new FakeNotificationHostGateway(NotificationResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Notification content is too large")
                .build());
        List<String> warnings = new ArrayList<>();
        DefaultPluginNotificationClient client = client(gateway, warnings);

        NotificationSendResult result = client.send(request(content));

        assertFalse(result.success());
        assertEquals("Notification content is too large", result.message());
        assertEquals(1, warnings.size());
        String warning = warnings.getFirst();
        assertTrue(warning.contains("pluginId=demo"));
        assertTrue(warning.contains("jobId=42"));
        assertTrue(warning.contains("requestId=request-a"));
        assertTrue(warning.contains("channelIds=[channel-a]"));
        assertTrue(warning.contains("contentBytes=" + content.getBytes(StandardCharsets.UTF_8).length));
        assertTrue(warning.contains("reason=Notification content is too large"));
        assertFalse(warning.contains("TOP-SECRET-CONTENT"));
    }

    @Test
    void multipleRejectedReceiptsAreCombinedAndSanitized() {
        String longReason = "provider refused\n" + "x".repeat(600);
        FakeNotificationHostGateway gateway = new FakeNotificationHostGateway(NotificationResponse.newBuilder()
                .setSuccess(false)
                .setMessage("Notification rejected\r\nby host")
                .addReceipts(NotificationSendReceipt.newBuilder()
                        .setChannelId("channel-disabled")
                        .setStatus("SKIPPED")
                        .setMessage("Notification channel is disabled")
                        .setHistoryId(10L)
                        .build())
                .addReceipts(NotificationSendReceipt.newBuilder()
                        .setChannelId("channel-failed")
                        .setStatus("FAILED")
                        .setMessage(longReason)
                        .setHistoryId(11L)
                        .build())
                .build());
        List<String> warnings = new ArrayList<>();
        DefaultPluginNotificationClient client = client(gateway, warnings);

        NotificationSendResult result = client.send(request("safe content"));

        assertFalse(result.success());
        assertEquals(1, warnings.size());
        String warning = warnings.getFirst();
        assertTrue(warning.contains("reason=Notification rejected  by host"));
        assertTrue(warning.contains("channel-disabled:SKIPPED(message=Notification channel is disabled, historyId=10)"));
        assertTrue(warning.contains("channel-failed:FAILED(message=provider refused "));
        assertTrue(warning.contains("...[truncated]"));
        assertFalse(warning.contains("\n"));
        assertFalse(warning.contains("\r"));
        assertTrue(warning.length() < 3_000);
    }

    @Test
    void successfulResponseDoesNotReportARejection() {
        FakeNotificationHostGateway gateway = new FakeNotificationHostGateway(NotificationResponse.newBuilder()
                .setSuccess(true)
                .setMessage("Notification queued")
                .addReceipts(NotificationSendReceipt.newBuilder()
                        .setChannelId("channel-a")
                        .setStatus("PENDING")
                        .setMessage("Notification queued")
                        .setHistoryId(12L)
                        .build())
                .build());
        List<String> warnings = new ArrayList<>();
        DefaultPluginNotificationClient client = client(gateway, warnings);

        NotificationSendResult result = client.send(request("safe content"));

        assertTrue(result.success());
        assertTrue(warnings.isEmpty());
    }

    private DefaultPluginNotificationClient client(FakeNotificationHostGateway gateway, List<String> warnings) {
        return new DefaultPluginNotificationClient("demo", "42", "request-a", gateway, warnings::add);
    }

    private NotificationSendRequest request(String content) {
        return new NotificationSendRequest(
                List.of("channel-a"),
                "标题",
                content,
                "INFO",
                Map.of("ignored", "not-logged")
        );
    }

    private static final class FakeNotificationHostGateway implements NotificationHostGateway {
        private final NotificationResponse response;

        private FakeNotificationHostGateway(NotificationResponse response) {
            this.response = response;
        }

        @Override
        public NotificationChannelListResponse listNotificationChannels(NotificationChannelListRequest request) {
            return NotificationChannelListResponse.getDefaultInstance();
        }

        @Override
        public NotificationResponse sendNotification(NotificationRequest request) {
            return response;
        }
    }
}
