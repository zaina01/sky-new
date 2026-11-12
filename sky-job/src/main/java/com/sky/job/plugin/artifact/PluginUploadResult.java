package com.sky.job.plugin.artifact;

import java.nio.file.Path;

public record PluginUploadResult(
        String pluginId,
        String version,
        String mainClass,
        String artifactSha256,
        Path artifactPath,
        int resolvedDependencyCount
) {
}
