package com.sky.job.plugin.artifact.dependency.dto;

import java.util.List;

public record PluginDependencyInstallRequest(
        List<String> dependencies,
        String mode
) {
}
