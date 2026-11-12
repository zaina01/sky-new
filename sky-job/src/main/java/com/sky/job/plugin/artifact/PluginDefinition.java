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
        LocalDateTime updatedAt
) {
}
