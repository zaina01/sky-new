package com.sky.job.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.NotificationChannel;
import com.sky.job.notification.NotificationChannelType;
import com.sky.job.notification.NotificationSendCommand;
import com.sky.job.notification.NotificationSendResult;
import com.sky.job.notification.NotificationSourceType;
import com.sky.job.system.DynamicNotificationSettings;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WebhookNotificationProviderTest {
    private static final int WECHAT_TEXT_MAX_BYTES = 2048;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void exactWechatByteLimitStaysSingleAndOneExtraByteSplits() throws Exception {
        WebhookNotificationProvider provider = provider();
        NotificationChannel channel = channel(config("https://example.test/hook", "WECHAT_WORK"));
        String exactContent = "a".repeat(WECHAT_TEXT_MAX_BYTES - utf8Bytes("T\n"));

        List<NotificationDeliveryPart> exact = provider.plan(channel, command("T", exactContent));
        List<NotificationDeliveryPart> over = provider.plan(channel, command("T", exactContent + "a"));

        assertThat(exact).containsExactly(NotificationDeliveryPart.single("T", exactContent));
        assertThat(over).hasSize(2);
        assertThat(over).extracting(NotificationDeliveryPart::title)
                .containsExactly("T [1/2]", "T [2/2]");
    }

    @Test
    void unicodeContentIsSplitWithoutLosingOrBreakingCharacters() throws Exception {
        WebhookNotificationProvider provider = provider();
        NotificationChannel channel = channel(config("https://example.test/hook", "WECHAT_ROBOT"));
        String content = "第一行🙂 内容保留\n第二行 数据继续 ".repeat(220);

        List<NotificationDeliveryPart> parts = provider.plan(channel, command("插件告警", content));

        assertThat(parts.size()).isGreaterThan(1);
        assertThat(parts.stream().map(NotificationDeliveryPart::content).reduce("", String::concat))
                .isEqualTo(content);
        for (int index = 0; index < parts.size(); index++) {
            NotificationDeliveryPart part = parts.get(index);
            assertThat(part.title()).isEqualTo("插件告警 [" + (index + 1) + "/" + parts.size() + "]");
            assertThat(utf8Bytes(part.title() + "\n" + part.content())).isLessThanOrEqualTo(WECHAT_TEXT_MAX_BYTES);
            assertThat(Character.isLowSurrogate(part.content().charAt(0))).isFalse();
            assertThat(Character.isHighSurrogate(part.content().charAt(part.content().length() - 1))).isFalse();
        }
    }

    @Test
    void explicitAndKeyDetectedWechatPayloadsSplitButGenericWebhookDoesNot() throws Exception {
        WebhookNotificationProvider provider = provider();
        NotificationSendCommand command = command("Title", "x".repeat(3000));

        List<NotificationDeliveryPart> explicit = provider.plan(
                channel(config("https://example.test/hook", "WECHAT_WORK")), command);
        List<NotificationDeliveryPart> detected = provider.plan(
                channel(config("https://example.test/hook?key=secret", "")), command);
        List<NotificationDeliveryPart> generic = provider.plan(
                channel(config("https://example.test/hook", "")), command);

        assertThat(explicit.size()).isGreaterThan(1);
        assertThat(detected.size()).isGreaterThan(1);
        assertThat(generic).containsExactly(NotificationDeliveryPart.single(command.title(), command.content()));
    }

    @Test
    void notificationRequiringMoreThanTwentyPartsIsRejected() throws Exception {
        WebhookNotificationProvider provider = provider();
        NotificationChannel channel = channel(config("https://example.test/hook", "WECHAT_WORK"));

        assertThatThrownBy(() -> provider.plan(channel, command("T", "x".repeat(WECHAT_TEXT_MAX_BYTES * 21))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WeChat webhook notification requires more than 20 chunks");
    }

    @Test
    void titleThatLeavesNoChunkSpaceIsRejected() throws Exception {
        WebhookNotificationProvider provider = provider();
        NotificationChannel channel = channel(config("https://example.test/hook", "WECHAT_WORK"));

        assertThatThrownBy(() -> provider.plan(channel, command("T".repeat(WECHAT_TEXT_MAX_BYTES), "content")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("WeChat webhook title is too large to split notification content");
    }

    @Test
    void plannedPartsAreSentAsValidWechatTextBodies() throws Exception {
        List<String> requestBodies = new ArrayList<>();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/webhook", exchange -> {
            requestBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = "{\"errcode\":0,\"errmsg\":\"ok\"}".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        server.start();
        try {
            String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/webhook";
            WebhookNotificationProvider provider = provider();
            NotificationChannel channel = channel(config(url, "WECHAT_WORK"));
            NotificationSendCommand original = command("任务结果", "中文🙂 with text and newlines\n".repeat(180));
            List<NotificationDeliveryPart> parts = provider.plan(channel, original);

            for (NotificationDeliveryPart part : parts) {
                NotificationSendResult result = provider.send(channel, command(part.title(), part.content()));
                assertThat(result.success()).isTrue();
            }

            assertThat(requestBodies).hasSize(parts.size());
            for (int index = 0; index < parts.size(); index++) {
                JsonNode body = objectMapper.readTree(requestBodies.get(index));
                String content = body.path("text").path("content").asText();
                assertThat(body.path("msgtype").asText()).isEqualTo("text");
                assertThat(content).isEqualTo(parts.get(index).title() + "\n" + parts.get(index).content());
                assertThat(utf8Bytes(content)).isLessThanOrEqualTo(WECHAT_TEXT_MAX_BYTES);
            }
        } finally {
            server.stop(0);
        }
    }

    private WebhookNotificationProvider provider() {
        DynamicNotificationSettings properties = mock(DynamicNotificationSettings.class);
        when(properties.httpTimeout()).thenReturn(Duration.ofSeconds(5));
        return new WebhookNotificationProvider(objectMapper, properties);
    }

    private NotificationChannel channel(String configJson) {
        LocalDateTime now = LocalDateTime.of(2026, 8, 10, 10, 0);
        return new NotificationChannel(
                1L, "webhook", NotificationChannelType.WEBHOOK, "Webhook", true, configJson, "",
                "owner", "owner", 1L, "Department", "owner", "owner", now, now);
    }

    private NotificationSendCommand command(String title, String content) {
        return new NotificationSendCommand(
                NotificationSourceType.PLUGIN_RUNTIME,
                "request-1",
                "installation-1",
                "plugin-1",
                "instance-1",
                "job-1",
                1L,
                "owner",
                1L,
                List.of("webhook"),
                title,
                content,
                "INFO",
                Map.of()
        );
    }

    private String config(String url, String payloadType) throws Exception {
        return objectMapper.writeValueAsString(Map.of("url", url, "payloadType", payloadType));
    }

    private int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
