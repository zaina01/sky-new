package com.sky.job.plugin.runtime;

import java.time.LocalDateTime;

public record PluginRuntimeRecord(
        long id,
        String pluginId,
        String instanceId,
        String version,
        String displayName,
        String mainClass,
        String artifactPath,
        String artifactSha256,
        String runtimeType,
        PluginRuntimeState state,
        String runnerHost,
        Integer runnerPort,
        String workDir,
        String capabilitiesJson,
        String configSchemaJson,
        String accountSchemaJson,
        String dependencyJarsJson,
        LocalDateTime startedAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
