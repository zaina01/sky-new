package com.sky.job.notification.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.NotificationChannel;
import com.sky.job.notification.NotificationChannelType;
import com.sky.job.system.DynamicNotificationSettings;
import com.sky.job.notification.NotificationSendCommand;
import com.sky.job.notification.NotificationSendResult;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class PushPlusNotificationProvider implements NotificationProvider {
    private static final URI PUSH_PLUS_URL = URI.create("https://www.pushplus.plus/send");

    private final ObjectMapper objectMapper;
    private final DynamicNotificationSettings properties;
    private final HttpClient httpClient;

    public PushPlusNotificationProvider(ObjectMapper objectMapper, DynamicNotificationSettings properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.PUSHPLUS;
    }

    @Override
    public void validateConfig(String configJson) {
        JsonNode root = read(configJson);
        if (text(root, "token").isBlank()) {
            throw new IllegalArgumentException("PushPlus token is required");
        }
    }

    @Override
    public NotificationSendResult send(NotificationChannel channel, NotificationSendCommand command) {
        try {
            JsonNode config = read(channel.configJson());
            HttpRequest request = HttpRequest.newBuilder(PUSH_PLUS_URL)
                    .timeout(properties.httpTimeout())
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body(config, command)))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            String statusCode = String.valueOf(response.statusCode());
            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                return NotificationSendResult.success(statusCode, "PushPlus accepted");
            }
            return NotificationSendResult.failed("HTTP_STATUS", "PushPlus returned non-success status", statusCode, "PushPlus failed");
        } catch (Exception exception) {
            return NotificationSendResult.failed(exception.getClass().getSimpleName(), exception.getMessage(), "", "PushPlus request failed");
        }
    }

    private String body(JsonNode config, NotificationSendCommand command) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("token", text(config, "token"));
        body.put("title", command.title());
        body.put("content", command.content());
        putIfPresent(body, "topic", text(config, "topic"));
        putIfPresent(body, "template", text(config, "template"));
        putIfPresent(body, "channel", text(config, "channel"));
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode read(String configJson) {
        try {
            return objectMapper.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid PushPlus config", exception);
        }
    }

    private String text(JsonNode root, String field) {
        return root.path(field).asText("").trim();
    }

    private void putIfPresent(Map<String, Object> body, String key, String value) {
        if (value != null && !value.isBlank()) {
            body.put(key, value);
        }
    }
}
