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
import java.util.UUID;

@Repository
public class PluginArtifactRegistry {
    private final PluginArtifactRegistryMapper mapper;
    private final ObjectMapper objectMapper;

    public PluginArtifactRegistry(PluginArtifactRegistryMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    public String register(PluginUploadManifest manifest, Set<PluginCapability> capabilities, Path artifactPath, String sha256, List<ResolvedPluginDependency> dependencies, boolean overwriteDependencies) {
        String instanceId = manifest.pluginId() + "-" + UUID.randomUUID();
        mapper.upsertRuntime(
                manifest.pluginId(),
                instanceId,
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
                overwriteDependencies
        );
        return instanceId;
    }

    public void updateDependencies(String pluginId, List<ResolvedPluginDependency> dependencies) {
        int updated = mapper.updateDependencyJars(pluginId, json(dependencies == null ? List.of() : dependencies));
        if (updated == 0) {
            throw new IllegalArgumentException("Plugin runtime not found");
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
