package com.sky.job.plugin.artifact.dto;

import com.sky.job.plugin.artifact.PluginDefinition;

import java.time.LocalDateTime;

public record PluginDefinitionResponse(
        String pluginId,
        String version,
        String displayName,
        String mainClass,
        String artifactSha256,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PluginDefinitionResponse from(PluginDefinition definition) {
        return new PluginDefinitionResponse(definition.pluginId(), definition.version(), definition.displayName(), definition.mainClass(), definition.artifactSha256(), definition.capabilitiesJson(), definition.configSchemaJson(), definition.accountSchemaJson(), definition.createdAt(), definition.updatedAt());
    }
}
