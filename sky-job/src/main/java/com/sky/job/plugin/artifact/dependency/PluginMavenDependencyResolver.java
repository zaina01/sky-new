package com.sky.job.plugin.artifact.dependency;

import com.sky.job.plugin.artifact.Sha256Digests;
import org.eclipse.aether.artifact.Artifact;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@EnableConfigurationProperties(PluginDependencyResolutionProperties.class)
public class PluginMavenDependencyResolver {
    private final PluginMavenResolver resolver;
    private final PluginDependencyResolutionProperties properties;

    public PluginMavenDependencyResolver(PluginMavenResolver resolver, PluginDependencyResolutionProperties properties) {
        this.resolver = resolver;
        this.properties = properties;
    }

    public List<ResolvedPluginDependency> resolve(List<PluginDependencyDeclaration> declarations) {
        if (declarations == null || declarations.isEmpty()) {
            return List.of();
        }
        Map<String, ResolvedPluginDependency> resolved = new LinkedHashMap<>();
        for (PluginDependencyDeclaration declaration : declarations) {
            for (Artifact artifact : resolver.resolveRuntimeArtifacts(declaration.coordinate())) {
                if (!"jar".equals(artifact.getExtension()) || artifact.getFile() == null) {
                    continue;
                }
                ResolvedPluginDependency dependency = resolvedDependency(artifact);
                String key = dependency.groupId() + ":" + dependency.artifactId() + ":" + dependency.classifier() + ":" + dependency.extension() + ":" + dependency.version();
                resolved.putIfAbsent(key, dependency);
            }
        }
        if (resolved.size() > properties.maxResolvedArtifacts()) {
            throw new IllegalArgumentException("Plugin resolves too many dependency artifacts");
        }
        return List.copyOf(new ArrayList<>(resolved.values()));
    }

    private ResolvedPluginDependency resolvedDependency(Artifact artifact) {
        Path repositoryRoot = properties.repositoryRoot().toAbsolutePath().normalize();
        Path artifactPath = artifact.getFile().toPath().toAbsolutePath().normalize();
        if (!artifactPath.startsWith(repositoryRoot)) {
            throw new IllegalArgumentException("Resolved plugin dependency escapes Maven repository root");
        }
        return new ResolvedPluginDependency(
                artifact.getGroupId(),
                artifact.getArtifactId(),
                artifact.getVersion(),
                artifact.getClassifier() == null ? "" : artifact.getClassifier(),
                artifact.getExtension(),
                repositoryRoot.relativize(artifactPath).toString().replace(java.io.File.separatorChar, '/'),
                Sha256Digests.digestHex(artifactPath)
        );
    }
}
