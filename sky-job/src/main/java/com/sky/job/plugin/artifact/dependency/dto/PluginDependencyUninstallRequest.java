package com.sky.job.plugin.artifact.dependency.dto;

import java.util.List;

public record PluginDependencyUninstallRequest(
        List<String> dependencies
) {
}
