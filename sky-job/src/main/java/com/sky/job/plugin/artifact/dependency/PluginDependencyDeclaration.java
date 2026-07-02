package com.sky.job.plugin.artifact.dependency;

public record PluginDependencyDeclaration(
        String groupId,
        String artifactId,
        String version
) {
    public String coordinate() {
        return groupId + ":" + artifactId + ":" + version;
    }
}
