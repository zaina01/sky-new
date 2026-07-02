package com.sky.job.plugin.storage;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.system.DynamicPluginStorageSettings;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

@Component
public class PluginPayloadValidator {
    private static final Pattern NAME_PATTERN = Pattern.compile("^[a-zA-Z0-9._-]{1,128}$");
    private static final Pattern KEY_PATTERN = Pattern.compile("^[a-zA-Z0-9._:-]{1,255}$");

    private final ObjectMapper objectMapper;
    private final DynamicPluginStorageSettings properties;

    public PluginPayloadValidator(ObjectMapper objectMapper, DynamicPluginStorageSettings properties) {
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public void validateCollection(String collection) {
        if (collection == null || !NAME_PATTERN.matcher(collection).matches()) {
            throw new IllegalArgumentException("Invalid collection name");
        }
    }

    public void validateKey(String key) {
        if (key == null || !KEY_PATTERN.matcher(key).matches()) {
            throw new IllegalArgumentException("Invalid data key");
        }
    }

    public void validateAccountId(String accountId) {
        if (accountId == null || !KEY_PATTERN.matcher(accountId).matches()) {
            throw new IllegalArgumentException("Invalid account id");
        }
    }

    public void validateDataJson(String json) {
        normalizeDataJson(json);
    }

    public void validateConfigJson(String json) {
        normalizeConfigJson(json);
    }

    public void validateAccountJson(String json) {
        normalizeAccountJson(json);
    }

    public String normalizeConfigJson(String json) {
        return normalizeJson(json, properties.maxConfigJsonBytes());
    }

    public String normalizeAccountJson(String json) {
        return normalizeJson(json, properties.maxAccountJsonBytes());
    }

    public String normalizeDataJson(String json) {
        return normalizeJson(json, properties.maxDataJsonBytes());
    }

    public String sanitizeLogMessage(String message) {
        String value = message == null ? "" : message.replaceAll("[\\p{Cntrl}&&[^\r\n\t]]", "?");
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        if (bytes.length <= properties.maxLogMessageBytes()) {
            return value;
        }
        return new String(bytes, 0, properties.maxLogMessageBytes(), StandardCharsets.UTF_8) + "...[truncated]";
    }

    private void validateJson(String json, int maxBytes) {
        normalizeJson(json, maxBytes);
    }

    private String normalizeJson(String json, int maxBytes) {
        String value = json == null || json.isBlank() ? "{}" : json;
        if (value.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
            throw new IllegalArgumentException("JSON payload is too large");
        }
        try {
            String normalized = objectMapper.writeValueAsString(objectMapper.readTree(value));
            if (normalized.getBytes(StandardCharsets.UTF_8).length > maxBytes) {
                throw new IllegalArgumentException("JSON payload is too large");
            }
            return normalized;
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid JSON payload", exception);
        }
    }
}
