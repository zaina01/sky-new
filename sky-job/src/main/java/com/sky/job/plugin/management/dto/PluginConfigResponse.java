package com.sky.job.plugin.management.dto;

public record PluginConfigResponse(
        String installationId,
        String pluginId,
        String configJson,
        String schemaJson
) {
}
