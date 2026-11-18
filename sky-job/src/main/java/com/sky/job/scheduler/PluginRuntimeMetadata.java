package com.sky.job.scheduler;

import com.sky.job.plugin.security.PluginCapability;

import java.nio.file.Path;
import java.util.List;
import java.util.Set;

public record PluginRuntimeMetadata(
        String installationId,
        String pluginId,
        String instanceId,
        String mainClass,
        Path artifactPath,
        String artifactSha256,
        List<Path> dependencyPaths,
        Set<PluginCapability> capabilities,
        boolean remoteDebugEnabled,
        int remoteDebugPort,
        boolean remoteDebugSuspend,
        String runtimeEngine,
        String entryPoint,
        String executionMode
) {
    public PluginRuntimeMetadata(
        String installationId,
        String pluginId,
        String instanceId,
        String mainClass,
        Path artifactPath,
        String artifactSha256,
        List<Path> dependencyPaths,
        Set<PluginCapability> capabilities,
        boolean remoteDebugEnabled,
        int remoteDebugPort,
        boolean remoteDebugSuspend,
        String runtimeEngine,
        String entryPoint
    ) {
        this(installationId, pluginId, instanceId, mainClass, artifactPath, artifactSha256, dependencyPaths, capabilities, remoteDebugEnabled, remoteDebugPort, remoteDebugSuspend, runtimeEngine, entryPoint, "plugin");
    }

    public PluginRuntimeMetadata(
        String installationId,
        String pluginId,
        String instanceId,
        String mainClass,
        Path artifactPath,
        String artifactSha256,
        List<Path> dependencyPaths,
        Set<PluginCapability> capabilities,
        boolean remoteDebugEnabled,
        int remoteDebugPort,
        boolean remoteDebugSuspend
    ) {
        this(installationId, pluginId, instanceId, mainClass, artifactPath, artifactSha256, dependencyPaths, capabilities, remoteDebugEnabled, remoteDebugPort, remoteDebugSuspend, "java", mainClass);
    }

}
