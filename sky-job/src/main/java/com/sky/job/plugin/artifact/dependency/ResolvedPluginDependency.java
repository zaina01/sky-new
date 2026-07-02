package com.sky.job.plugin.artifact.dependency;

public record ResolvedPluginDependency(
        String groupId,
        String artifactId,
        String version,
        String classifier,
        String extension,
        String relativePath,
        String sha256
) {
}
