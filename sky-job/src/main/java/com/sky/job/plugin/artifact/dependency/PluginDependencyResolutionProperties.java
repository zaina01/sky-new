package com.sky.job.plugin.artifact.dependency;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.nio.file.Path;
import java.util.List;

@ConfigurationProperties(prefix = "sky.plugin.dependencies")
public record PluginDependencyResolutionProperties(
        Path repositoryRoot,
        List<String> remoteRepositories,
        int maxDeclaredDependencies,
        int maxResolvedArtifacts,
        boolean allowSnapshots
) {
    public PluginDependencyResolutionProperties {
        if (repositoryRoot == null) repositoryRoot = Path.of("./data/plugins/maven-repository");
        if (remoteRepositories == null || remoteRepositories.isEmpty()) remoteRepositories = List.of("https://repo1.maven.org/maven2/");
        if (maxDeclaredDependencies <= 0) maxDeclaredDependencies = 50;
        if (maxResolvedArtifacts <= 0) maxResolvedArtifacts = 300;
    }
}
