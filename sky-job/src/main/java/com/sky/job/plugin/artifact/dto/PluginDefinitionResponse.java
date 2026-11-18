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
        LocalDateTime updatedAt,
        String runtimeEngine,
        String entryPoint,
        String executionMode
) {
    public PluginDefinitionResponse(
        String pluginId,
        String version,
        String displayName,
        String mainClass,
        String artifactSha256,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String runtimeEngine,
        String entryPoint
    ) {
        this(pluginId, version, displayName, mainClass, artifactSha256, capabilitiesJson, configSchemaJson, accountSchemaJson, createdAt, updatedAt, runtimeEngine, entryPoint, "plugin");
    }

    public PluginDefinitionResponse(
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
        this(pluginId, version, displayName, mainClass, artifactSha256, capabilitiesJson, configSchemaJson, accountSchemaJson, createdAt, updatedAt, "java", mainClass);
    }

    public static PluginDefinitionResponse from(PluginDefinition definition) {
        return new PluginDefinitionResponse(definition.pluginId(), definition.version(), definition.displayName(), definition.mainClass(), definition.artifactSha256(), definition.capabilitiesJson(), definition.configSchemaJson(), definition.accountSchemaJson(), definition.createdAt(), definition.updatedAt(), definition.runtimeEngine(), definition.entryPoint(), definition.executionMode());
    }
}
