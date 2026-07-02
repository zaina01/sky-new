package com.sky.job.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;

import java.util.Iterator;
import java.util.Map;
import java.util.regex.Pattern;

@Component
public class NotificationSecretMasker {
    private static final Pattern TOKEN_PATTERN = Pattern.compile("(?i)(token|password|secret|authorization|bearer|key)(\\s*[:=]\\s*)[^\\s,;&}]+ ");
    private final ObjectMapper objectMapper;

    public NotificationSecretMasker(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String maskConfig(String configJson) {
        try {
            JsonNode root = objectMapper.readTree(configJson == null || configJson.isBlank() ? "{}" : configJson);
            mask(root);
            return objectMapper.writeValueAsString(root);
        } catch (Exception exception) {
            return "{}";
        }
    }

    public String maskText(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return TOKEN_PATTERN.matcher(value + " ").replaceAll("$1$2****** ").trim();
    }

    private void mask(JsonNode node) {
        if (node instanceof ObjectNode objectNode) {
            Iterator<Map.Entry<String, JsonNode>> fields = objectNode.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                if (isSecretKey(field.getKey())) {
                    objectNode.put(field.getKey(), "******");
                } else {
                    mask(field.getValue());
                }
            }
        } else if (node.isArray()) {
            node.forEach(this::mask);
        }
    }

    private boolean isSecretKey(String key) {
        String lower = key == null ? "" : key.toLowerCase();
        return lower.contains("token") || lower.contains("secret") || lower.contains("password") || lower.contains("authorization") || lower.contains("key") || lower.contains("header") || lower.contains("url");
    }
}
