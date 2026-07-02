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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@Component
public class WebhookNotificationProvider implements NotificationProvider {
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final DynamicNotificationSettings properties;

    public WebhookNotificationProvider(ObjectMapper objectMapper, DynamicNotificationSettings properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
        this.httpClient = HttpClient.newHttpClient();
    }

    @Override
    public NotificationChannelType type() {
        return NotificationChannelType.WEBHOOK;
    }

    @Override
    public void validateConfig(String configJson) {
        JsonNode root = read(configJson);
        String url = text(root, "url");
        if (url.isBlank()) {
            throw new IllegalArgumentException("Webhook url is required");
        }
        URI.create(url);
    }

    @Override
    public NotificationSendResult send(NotificationChannel channel, NotificationSendCommand command) {
        try {
            JsonNode config = read(channel.configJson());
            URI uri = URI.create(text(config, "url"));
            HttpRequest.Builder builder = HttpRequest.newBuilder(uri)
                    .timeout(timeout(config))
                    .method(method(config), HttpRequest.BodyPublishers.ofString(body(config, uri, command)))
                    .header("Content-Type", "application/json");
            JsonNode headers = config.path("headers");
            if (headers.isObject()) {
                headers.fields().forEachRemaining(entry -> builder.header(entry.getKey(), entry.getValue().asText("")));
            }
            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            String statusCode = String.valueOf(response.statusCode());
            String responseBody = preview(response.body());
            if (response.statusCode() >= 200 && response.statusCode() < 300 && accepted(response.body())) {
                return NotificationSendResult.success(statusCode, responseBody.isBlank() ? "Webhook accepted" : responseBody);
            }
            return NotificationSendResult.failed("WEBHOOK_REJECTED", responseBody.isBlank() ? "Webhook returned non-success response" : responseBody, statusCode, "Webhook failed");
        } catch (Exception exception) {
            return NotificationSendResult.failed(exception.getClass().getSimpleName(), exception.getMessage(), "", "Webhook request failed");
        }
    }

    private String body(JsonNode config, URI uri, NotificationSendCommand command) throws Exception {
        String payloadType = text(config, "payloadType");
        if (payloadType.equalsIgnoreCase("WECHAT_WORK") || payloadType.equalsIgnoreCase("WECHAT_ROBOT") || (payloadType.isBlank() && hasQueryParam(uri, "key"))) {
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("content", command.title() + "\n" + command.content());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("msgtype", "text");
            body.put("text", text);
            return objectMapper.writeValueAsString(body);
        }
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", command.title());
        body.put("content", command.content());
        body.put("severity", command.severity());
        body.put("sourceType", command.sourceType().name());
        body.put("pluginId", command.pluginId());
        body.put("jobId", command.jobId());
        body.put("executionId", command.executionId());
        body.put("labels", command.labels());
        body.put("createdAt", Instant.now().toString());
        return objectMapper.writeValueAsString(body);
    }

    private JsonNode read(String configJson) {
        try {
            return objectMapper.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid webhook config", exception);
        }
    }

    private boolean accepted(String responseBody) {
        if (responseBody == null || responseBody.isBlank()) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(responseBody);
            if (root.has("errcode")) {
                return root.path("errcode").asInt(-1) == 0;
            }
            if (root.has("code") && root.path("code").canConvertToInt()) {
                return root.path("code").asInt(-1) == 0;
            }
            if (root.has("success")) {
                return root.path("success").asBoolean(false);
            }
            return true;
        } catch (Exception exception) {
            return true;
        }
    }

    private boolean hasQueryParam(URI uri, String name) {
        String query = uri.getRawQuery();
        if (query == null || query.isBlank()) {
            return false;
        }
        for (String part : query.split("&")) {
            int separator = part.indexOf('=');
            String key = separator >= 0 ? part.substring(0, separator) : part;
            if (name.equals(URLDecoder.decode(key, StandardCharsets.UTF_8))) {
                return true;
            }
        }
        return false;
    }

    private String preview(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.length() <= 500 ? value : value.substring(0, 500) + "...[truncated]";
    }

    private String text(JsonNode root, String field) {
        return root.path(field).asText("").trim();
    }

    private String method(JsonNode config) {
        String method = text(config, "method");
        return method.isBlank() ? "POST" : method.toUpperCase();
    }

    private Duration timeout(JsonNode config) {
        long timeoutMillis = config.path("timeoutMillis").asLong(properties.httpTimeout().toMillis());
        return Duration.ofMillis(Math.max(timeoutMillis, 1000));
    }
}
