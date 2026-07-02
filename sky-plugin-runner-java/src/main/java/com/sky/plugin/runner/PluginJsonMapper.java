package com.sky.plugin.runner;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import com.sky.plugin.api.AccountId;

import java.lang.reflect.Field;

final class PluginJsonMapper {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private PluginJsonMapper() {
    }

    static <T> T read(String json, Class<T> type) {
        try {
            return OBJECT_MAPPER.readValue(json == null || json.isBlank() ? "{}" : json, type);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Failed to deserialize plugin JSON as " + type.getName(), exception);
        }
    }

    static String write(Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Plugin JSON value must not be null");
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(value);
        } catch (JacksonException exception) {
            throw new IllegalArgumentException("Failed to serialize plugin JSON value: " + value.getClass().getName(), exception);
        }
    }

    static String accountId(Object account) {
        if (account == null) {
            throw new IllegalArgumentException("Plugin account must not be null");
        }
        Field accountIdField = null;
        for (Field field : account.getClass().getDeclaredFields()) {
            if (!field.isAnnotationPresent(AccountId.class)) {
                continue;
            }
            if (accountIdField != null) {
                throw new IllegalArgumentException("Plugin account class must declare at most one @AccountId field");
            }
            accountIdField = field;
        }
        if (accountIdField == null) {
            throw new IllegalArgumentException("Plugin account class must declare an @AccountId field");
        }
        try {
            accountIdField.setAccessible(true);
            Object value = accountIdField.get(account);
            if (value == null || value.toString().isBlank()) {
                throw new IllegalArgumentException("Plugin account @AccountId value must not be blank");
            }
            return value.toString();
        } catch (IllegalAccessException exception) {
            throw new IllegalArgumentException("Failed to read plugin account @AccountId field", exception);
        }
    }
}
