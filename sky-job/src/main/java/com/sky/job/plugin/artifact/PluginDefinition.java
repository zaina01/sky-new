package com.sky.job.plugin.artifact;

import java.time.LocalDateTime;

public record PluginDefinition(
        String pluginId,
        String version,
        String displayName,
        String mainClass,
        String artifactPath,
        String artifactSha256,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        String dependencyJarsJson,
        String uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String runtimeEngine,
        String entryPoint,
        String executionMode
) {
    public PluginDefinition(
        String pluginId,
        String version,
        String displayName,
        String mainClass,
        String artifactPath,
        String artifactSha256,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        String dependencyJarsJson,
        String uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String runtimeEngine,
        String entryPoint
    ) {
        this(pluginId, version, displayName, mainClass, artifactPath, artifactSha256, capabilitiesJson, configSchemaJson, accountSchemaJson, dependencyJarsJson, uploadedBy, createdAt, updatedAt, runtimeEngine, entryPoint, "plugin");
    }

    public PluginDefinition(
        String pluginId,
        String version,
        String displayName,
        String mainClass,
        String artifactPath,
        String artifactSha256,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        String dependencyJarsJson,
        String uploadedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this(pluginId, version, displayName, mainClass, artifactPath, artifactSha256, capabilitiesJson, configSchemaJson, accountSchemaJson, dependencyJarsJson, uploadedBy, createdAt, updatedAt, "java", mainClass);
    }

}
