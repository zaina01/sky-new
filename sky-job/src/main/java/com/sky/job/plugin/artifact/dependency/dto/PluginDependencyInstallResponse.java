package com.sky.job.plugin.artifact.dependency.dto;

import java.util.List;

public record PluginDependencyInstallResponse(
        String pluginId,
        int declaredDependencyCount,
        int resolvedDependencyCount,
        List<PluginDependencyResponse> dependencies
) {
}
