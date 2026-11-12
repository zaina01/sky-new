package com.sky.job.plugin.artifact;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.plugin.artifact.dependency.ResolvedPluginDependency;
import com.sky.job.plugin.artifact.mapper.PluginArtifactRegistryMapper;
import com.sky.job.plugin.security.PluginCapability;
import org.springframework.stereotype.Repository;

import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Repository
public class PluginArtifactRegistry {
    private final PluginArtifactRegistryMapper mapper;
    private final ObjectMapper objectMapper;

    public PluginArtifactRegistry(PluginArtifactRegistryMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public String register(PluginUploadManifest manifest, Set<PluginCapability> capabilities, Path artifactPath, String sha256, List<ResolvedPluginDependency> dependencies, boolean overwriteDependencies, String uploadedBy) {
        mapper.upsertDefinition(
                manifest.pluginId(),
                manifest.version(),
                manifest.displayName() == null ? "" : manifest.displayName(),
                manifest.runtime().mainClass(),
                artifactPath.toAbsolutePath().normalize().toString(),
                sha256,
                json(Map.of(
                        "task", capabilities.contains(PluginCapability.TASK),
                        "config", capabilities.contains(PluginCapability.CONFIG),
                        "account", capabilities.contains(PluginCapability.ACCOUNT),
                        "notification", capabilities.contains(PluginCapability.NOTIFICATION),
                        "service", capabilities.contains(PluginCapability.SERVICE)
                )),
                jsonNode(manifest.configSchema()),
                jsonNode(manifest.accountSchema()),
                json(dependencies == null ? List.of() : dependencies),
                overwriteDependencies,
                uploadedBy == null ? "" : uploadedBy
        );
        return manifest.pluginId();
    }

    public java.util.Optional<PluginDefinition> find(String pluginId) {
        return java.util.Optional.ofNullable(mapper.findDefinition(pluginId));
    }

    public java.util.List<PluginDefinition> list(String keyword, Integer page, Integer size) {
        int p = Math.max(page == null ? 1 : page, 1);
        int s = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return mapper.listDefinitions(normalized, normalized == null ? null : "%" + normalized + "%", s, (p - 1) * s);
    }

    public long count(String keyword) {
        String normalized = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return mapper.countDefinitions(normalized, normalized == null ? null : "%" + normalized + "%");
    }

    public boolean hasInstallations(String pluginId) {
        return mapper.countInstallations(pluginId) > 0;
    }

    public int delete(String pluginId) {
        return mapper.deleteDefinition(pluginId);
    }

    public void updateDependencies(String pluginId, List<ResolvedPluginDependency> dependencies) {
        int updated = mapper.updateDependencyJars(pluginId, json(dependencies == null ? List.of() : dependencies));
        if (updated == 0) {
            throw new IllegalArgumentException("Plugin definition not found");
        }
    }

    private String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Failed to serialize plugin metadata", exception);
        }
    }

    private String jsonNode(Object value) {
        return value == null ? "{}" : json(value);
    }
}
