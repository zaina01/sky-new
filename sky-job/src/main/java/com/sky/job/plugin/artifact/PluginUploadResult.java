package com.sky.job.plugin.artifact;

import java.nio.file.Path;

public record PluginUploadResult(
        String pluginId,
        String version,
        String mainClass,
        String artifactSha256,
        Path artifactPath,
        int resolvedDependencyCount,
        String runtimeEngine,
        String entryPoint,
        String executionMode
) {
    public PluginUploadResult(
        String pluginId,
        String version,
        String mainClass,
        String artifactSha256,
        Path artifactPath,
        int resolvedDependencyCount,
        String runtimeEngine,
        String entryPoint
    ) {
        this(pluginId, version, mainClass, artifactSha256, artifactPath, resolvedDependencyCount, runtimeEngine, entryPoint, "plugin");
    }

}
