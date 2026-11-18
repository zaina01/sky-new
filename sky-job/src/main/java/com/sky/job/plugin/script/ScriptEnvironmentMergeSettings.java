package com.sky.job.plugin.script;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;

/** The same grouping rules are used for size validation and execution. */
record ScriptEnvironmentMergeSettings(String defaultSeparator, Map<String, String> overrides) {
    static ScriptEnvironmentMergeSettings fromOptions(ObjectNode options) {
        return options.has("environmentSettings") ? parse(options.get("environmentSettings"))
                : new ScriptEnvironmentMergeSettings("@", Map.of());
    }

    static ScriptEnvironmentMergeSettings parse(JsonNode request) {
        if (request == null || !request.isObject()) {
            throw new IllegalArgumentException("Environment merge settings object required");
        }
        String separator = separator(request.path("defaultSeparator"));
        JsonNode rules = request.path("overrides");
        if (!rules.isArray() || rules.size() > 128) {
            throw new IllegalArgumentException("overrides must be an array of at most 128 rules");
        }
        Map<String, String> overrides = new TreeMap<>();
        Map<String, String> names = new LinkedHashMap<>();
        for (JsonNode rule : rules) {
            if (!rule.path("name").isTextual()) throw new IllegalArgumentException("Variable name must be text");
            String name = rule.path("name").asText();
            registerName(names, name);
            if (overrides.putIfAbsent(name, separator(rule.path("separator"))) != null) {
                throw new IllegalArgumentException("Duplicate separator override name");
            }
        }
        return new ScriptEnvironmentMergeSettings(separator, Map.copyOf(overrides));
    }

    private static String separator(JsonNode value) {
        if (!value.isTextual() || value.asText().indexOf('\0') >= 0
                || bytes(value.asText()) > 128) {
            throw new IllegalArgumentException("Separator must be text without NUL, at most 128 UTF-8 bytes");
        }
        return value.asText();
    }

    ObjectNode toJson(ObjectMapper json) {
        ObjectNode result = json.createObjectNode().put("defaultSeparator", defaultSeparator);
        var rules = result.putArray("overrides");
        new TreeMap<>(overrides).forEach((name, value) -> rules.addObject().put("name", name).put("separator", value));
        return result;
    }

    String separatorFor(String name) {
        return overrides.getOrDefault(name, defaultSeparator);
    }

    void validateNames(List<ObjectNode> rows) {
        Map<String, String> names = new LinkedHashMap<>();
        overrides.keySet().forEach(name -> registerName(names, name));
        rows.forEach(row -> registerName(names, row.path("name").asText()));
    }

    private static void registerName(Map<String, String> names, String name) {
        ScriptOptionsValidation.validateName(name);
        String existing = names.putIfAbsent(name.toUpperCase(Locale.ROOT), name);
        if (existing != null && !existing.equals(name)) {
            throw new IllegalArgumentException("Environment variable names conflict by case, including disabled variables and separator overrides");
        }
    }

    static int bytes(String value) {
        return value.getBytes(StandardCharsets.UTF_8).length;
    }
}
