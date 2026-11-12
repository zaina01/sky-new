package com.sky.job.plugin.management.dto;

public record PluginAccountSchemaResponse(
        String installationId,
        String pluginId,
        String schemaJson
) {
}
