package com.sky.job.scheduler;

import com.sky.job.plugin.security.PluginCapability;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record PluginRuntimeMetadata(
        String pluginId,
        String instanceId,
        String mainClass,
        Path artifactPath,
        String artifactSha256,
        List<Path> dependencyPaths,
        Set<PluginCapability> capabilities
) {
}
