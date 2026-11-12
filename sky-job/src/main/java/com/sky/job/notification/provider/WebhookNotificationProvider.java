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
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class WebhookNotificationProvider implements NotificationProvider {
    private static final int WECHAT_TEXT_MAX_BYTES = 2048;
    private static final int WECHAT_MAX_PARTS = 20;

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
    public List<NotificationDeliveryPart> plan(NotificationChannel channel, NotificationSendCommand command) {
        JsonNode config = read(channel.configJson());
        URI uri = URI.create(text(config, "url"));
        if (!isWechatPayload(config, uri)) {
            return NotificationProvider.super.plan(channel, command);
        }
        if (utf8Bytes(renderWechatContent(command.title(), command.content())) <= WECHAT_TEXT_MAX_BYTES) {
            return NotificationProvider.super.plan(channel, command);
        }
        return splitWechatText(command.title(), command.content());
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
        if (isWechatPayload(config, uri)) {
            Map<String, Object> text = new LinkedHashMap<>();
            text.put("content", renderWechatContent(command.title(), command.content()));
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

    private List<NotificationDeliveryPart> splitWechatText(String title, String content) {
        List<String> chunks = null;
        for (int assumedPartCount = 2; assumedPartCount <= WECHAT_MAX_PARTS; assumedPartCount++) {
            List<String> candidate = splitContent(title, content, assumedPartCount, true);
            if (candidate.size() <= assumedPartCount) {
                chunks = candidate;
                break;
            }
        }
        if (chunks == null) {
            List<String> hardSplit = splitContent(title, content, WECHAT_MAX_PARTS, false);
            if (hardSplit.size() <= WECHAT_MAX_PARTS) {
                chunks = hardSplit;
            }
        }
        if (chunks == null) {
            throw new IllegalArgumentException("WeChat webhook notification requires more than 20 chunks");
        }

        int partCount = chunks.size();
        List<NotificationDeliveryPart> parts = new ArrayList<>(partCount);
        for (int index = 0; index < partCount; index++) {
            String partTitle = partTitle(title, index + 1, partCount);
            String chunk = chunks.get(index);
            if (utf8Bytes(renderWechatContent(partTitle, chunk)) > WECHAT_TEXT_MAX_BYTES) {
                throw new IllegalStateException("WeChat webhook chunk exceeds 2048 UTF-8 bytes");
            }
            parts.add(new NotificationDeliveryPart(partTitle, chunk, index + 1, partCount));
        }
        return List.copyOf(parts);
    }

    private List<String> splitContent(String title, String content, int assumedPartCount, boolean preferNaturalBoundary) {
        List<String> chunks = new ArrayList<>(assumedPartCount);
        int offset = 0;
        while (offset < content.length()) {
            int partIndex = chunks.size() + 1;
            String plannedTitle = partTitle(title, partIndex, assumedPartCount);
            int byteBudget = WECHAT_TEXT_MAX_BYTES - utf8Bytes(plannedTitle + "\n");
            if (byteBudget <= 0) {
                throw new IllegalArgumentException("WeChat webhook title is too large to split notification content");
            }
            int hardEnd = endWithinUtf8Budget(content, offset, byteBudget);
            if (hardEnd == offset) {
                throw new IllegalArgumentException("WeChat webhook title leaves no room for notification content");
            }
            int end = hardEnd;
            if (preferNaturalBoundary && hardEnd < content.length()) {
                end = preferredBoundary(content, offset, hardEnd, byteBudget);
            }
            chunks.add(content.substring(offset, end));
            offset = end;
            if (chunks.size() > assumedPartCount) {
                return chunks;
            }
        }
        return chunks;
    }

    private int endWithinUtf8Budget(String value, int start, int byteBudget) {
        int offset = start;
        int usedBytes = 0;
        while (offset < value.length()) {
            int codePoint = value.codePointAt(offset);
            int codePointBytes = utf8Length(codePoint);
            if (usedBytes + codePointBytes > byteBudget) {
                break;
            }
            usedBytes += codePointBytes;
            offset += Character.charCount(codePoint);
        }
        return offset;
    }

    private int preferredBoundary(String value, int start, int hardEnd, int byteBudget) {
        int cursor = hardEnd;
        int scannedBytes = 0;
        int maxRewindBytes = Math.max(byteBudget / 4, 1);
        while (cursor > start) {
            int codePoint = value.codePointBefore(cursor);
            int codePointStart = cursor - Character.charCount(codePoint);
            scannedBytes += utf8Length(codePoint);
            if (scannedBytes > maxRewindBytes) {
                break;
            }
            if (Character.isWhitespace(codePoint)) {
                return cursor;
            }
            cursor = codePointStart;
        }
        return hardEnd;
    }

    private String partTitle(String title, int partIndex, int partCount) {
        return title + " [" + partIndex + "/" + partCount + "]";
    }

    private String renderWechatContent(String title, String content) {
        return title + "\n" + content;
    }

    private boolean isWechatPayload(JsonNode config, URI uri) {
        String payloadType = text(config, "payloadType");
        return payloadType.equalsIgnoreCase("WECHAT_WORK")
                || payloadType.equalsIgnoreCase("WECHAT_ROBOT")
                || (payloadType.isBlank() && hasQueryParam(uri, "key"));
    }

    private int utf8Bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }

    private int utf8Length(int codePoint) {
        if (codePoint <= 0x7f) {
            return 1;
        }
        if (codePoint <= 0x7ff) {
            return 2;
        }
        if (codePoint >= Character.MIN_SURROGATE && codePoint <= Character.MAX_SURROGATE) {
            return 1;
        }
        return codePoint <= 0xffff ? 3 : 4;
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
