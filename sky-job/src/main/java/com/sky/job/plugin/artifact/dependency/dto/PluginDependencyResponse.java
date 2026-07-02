package com.sky.job.plugin.artifact.dependency.dto;

public record PluginDependencyResponse(
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String extension,
        String relativePath,
        String sha256
) {
}
