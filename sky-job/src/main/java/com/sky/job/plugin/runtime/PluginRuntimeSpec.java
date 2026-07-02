package com.sky.job.plugin.runtime;

import com.sky.job.plugin.security.PluginCapability;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record PluginRuntimeSpec(
        String pluginId,
        String instanceId,
        String mainClass,
        Path artifactPath,
        String artifactSha256,
        Path workDir,
        int hostGrpcPort,
        List<Path> dependencyPaths,
        Set<PluginCapability> capabilities,
        String runId
) {
    public PluginRuntimeSpec {
        runId = runId == null || runId.isBlank() ? java.util.UUID.randomUUID().toString() : runId;
    }
}
