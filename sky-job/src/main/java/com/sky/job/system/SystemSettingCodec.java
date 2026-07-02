package com.sky.job.system;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.quartz.CronExpression;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Locale;

@Component
public class SystemSettingCodec {
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };

    private final ObjectMapper objectMapper;

    public SystemSettingCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public Object parse(SystemSettingValueType valueType, Object value) {
        if (value == null) {
            throw new IllegalArgumentException("Setting value is required");
        }
        return switch (valueType) {
            case BOOLEAN -> parseBoolean(value);
            case INTEGER -> parseInteger(value);
            case LONG -> parseLong(value);
            case DURATION -> parseDuration(value);
            case CRON -> parseCron(value);
            case STRING -> parseString(value);
            case STRING_LIST -> parseStringList(value);
        };
    }

    public String format(SystemSettingValueType valueType, Object value) {
        Object parsed = parse(valueType, value);
        return switch (valueType) {
            case BOOLEAN, INTEGER, LONG, CRON, STRING -> String.valueOf(parsed);
            case DURATION -> ((Duration) parsed).toString();
            case STRING_LIST -> writeStringList(parsed);
        };
    }

    @SuppressWarnings("unchecked")
    public Object typedValue(SystemSettingValueType valueType, String valueText) {
        return switch (valueType) {
            case BOOLEAN -> parseBoolean(valueText);
            case INTEGER -> parseInteger(valueText);
            case LONG -> parseLong(valueText);
            case DURATION -> parseDuration(valueText).toString();
            case CRON, STRING -> valueText == null ? "" : valueText;
            case STRING_LIST -> parseStringList(valueText);
        };
    }

    private boolean parseBoolean(Object value) {
        if (value instanceof Boolean booleanValue) {
            return booleanValue;
        }
        String text = parseString(value).toLowerCase(Locale.ROOT);
        if ("true".equals(text)) {
            return true;
        }
        if ("false".equals(text)) {
            return false;
        }
        throw new IllegalArgumentException("Invalid boolean setting value");
    }

    private int parseInteger(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(parseString(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid integer setting value", exception);
        }
    }

    private long parseLong(Object value) {
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(parseString(value));
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid long setting value", exception);
        }
    }

    private Duration parseDuration(Object value) {
        if (value instanceof Duration duration) {
            return duration;
        }
        try {
            return DurationStyle.detectAndParse(parseString(value));
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException("Invalid duration setting value", exception);
        }
    }

    private String parseCron(Object value) {
        String text = parseString(value);
        if (!CronExpression.isValidExpression(text)) {
            throw new IllegalArgumentException("Invalid cron setting value");
        }
        return text;
    }

    private String parseString(Object value) {
        if (value instanceof String string) {
            return string.trim();
        }
        return String.valueOf(value).trim();
    }

    private List<String> parseStringList(Object value) {
        try {
            if (value instanceof String string) {
                if (string.isBlank()) {
                    return List.of();
                }
                String trimmed = string.trim();
                if (trimmed.startsWith("[")) {
                    return normalizeStringList(objectMapper.readValue(trimmed, STRING_LIST_TYPE));
                }
                return normalizeStringList(List.of(trimmed.split(",")));
            }
            return normalizeStringList(objectMapper.convertValue(value, STRING_LIST_TYPE));
        } catch (IllegalArgumentException | JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid string list setting value", exception);
        }
    }

    private List<String> normalizeStringList(List<String> values) {
        if (values == null) {
            return List.of();
        }
        return values.stream()
                .filter(value -> value != null && !value.isBlank())
                .map(String::trim)
                .distinct()
                .toList();
    }

    private String writeStringList(Object value) {
        try {
            return objectMapper.writeValueAsString(parseStringList(value));
        } catch (JsonProcessingException exception) {
            throw new IllegalArgumentException("Invalid string list setting value", exception);
        }
    }
}
