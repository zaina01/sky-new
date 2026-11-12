package com.sky.job.scheduler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.common.mapper.StatusCountRow;
import com.sky.job.plugin.artifact.dependency.PluginDependencyResolutionProperties;
import com.sky.job.plugin.runtime.PluginOptionUsage;
import com.sky.job.plugin.runtime.PluginRuntimeRecord;
import com.sky.job.plugin.runtime.PluginRuntimeState;
import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.scheduler.mapper.PluginRuntimeMetadataMapper;
import com.sky.job.scheduler.mapper.PluginRuntimeRow;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Repository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import com.sky.job.security.EffectiveDataScope;

@Repository
@EnableConfigurationProperties(PluginDependencyResolutionProperties.class)
public class PluginRuntimeMetadataStore {
    private final PluginRuntimeMetadataMapper mapper;
    private final ObjectMapper objectMapper;
    private final PluginDependencyResolutionProperties dependencyProperties;

    public PluginRuntimeMetadataStore(PluginRuntimeMetadataMapper mapper, ObjectMapper objectMapper, PluginDependencyResolutionProperties dependencyProperties) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
        this.dependencyProperties = dependencyProperties;
    }

    public Optional<PluginRuntimeMetadata> findByInstallationId(String installationId) {
        return Optional.ofNullable(mapper.findByInstallationId(installationId)).map(this::map);
    }

    public Optional<PluginRuntimeRecord> findRuntimeByInstallationId(String installationId) {
        return Optional.ofNullable(mapper.findByInstallationId(installationId)).map(this::mapRuntime);
    }

    public List<PluginRuntimeRecord> listRuntimes(String pluginId, PluginRuntimeState state, EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        String stateName = state == null ? null : state.name();
        return mapper.listRuntimes(trim(pluginId), stateName, scope, normalizedSize, (normalizedPage - 1) * normalizedSize).stream()
                .map(this::mapRuntime)
                .toList();
    }

    public long countRuntimes(String pluginId, PluginRuntimeState state, EffectiveDataScope scope) {
        return mapper.countRuntimes(trim(pluginId), state == null ? null : state.name(), scope);
    }

    public List<StatusCountRow> countRuntimesByState(EffectiveDataScope scope) {
        return mapper.countRuntimesByState(scope);
    }

    public List<PluginRuntimeRecord> listOptions(String keyword, PluginRuntimeState state, PluginOptionUsage usage, EffectiveDataScope scope, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.listOptions(trim(keyword), like(keyword), state == null ? null : state.name(), usage == null ? null : usage.name(), scope, normalizedSize, (normalizedPage - 1) * normalizedSize).stream()
                .map(this::mapRuntime)
                .toList();
    }

    public long countOptions(String keyword, PluginRuntimeState state, PluginOptionUsage usage, EffectiveDataScope scope) {
        return mapper.countOptions(trim(keyword), like(keyword), state == null ? null : state.name(), usage == null ? null : usage.name(), scope);
    }

    public void updateLifecycle(String installationId, PluginRuntimeState state, String runnerHost, Integer runnerPort, Path workDir) {
        mapper.updateLifecycle(
                installationId,
                state.name(),
                runnerHost == null ? "127.0.0.1" : runnerHost,
                runnerPort,
                workDir == null ? "" : workDir.toAbsolutePath().normalize().toString(),
                state == PluginRuntimeState.RUNNING ? LocalDateTime.now() : null);
    }

    public int stopStaleRunningRuntimes() {
        return mapper.stopStaleRunningRuntimes();
    }

    public int softDelete(String installationId) {
        return mapper.softDelete(installationId);
    }

    private PluginRuntimeMetadata map(PluginRuntimeRow row) {
        Set<PluginCapability> capabilities = capabilities(row.capabilitiesJson());
        if (!"process".equals(row.runtimeType())) {
            throw new IllegalArgumentException("Plugin runtime must use process type");
        }
        if (row.artifactPath() == null || row.artifactPath().isBlank() || row.artifactSha256() == null || row.artifactSha256().isBlank()) {
            throw new IllegalArgumentException("Plugin runtime artifact metadata is incomplete");
        }
        return new PluginRuntimeMetadata(
                row.installationId(),
                row.pluginId(),
                row.instanceId(),
                row.mainClass(),
                Path.of(row.artifactPath()),
                row.artifactSha256(),
                dependencyPaths(row.dependencyJarsJson()),
                capabilities,
                row.remoteDebugEnabled(),
                row.remoteDebugPort(),
                row.remoteDebugSuspend()
        );
    }

    private PluginRuntimeRecord mapRuntime(PluginRuntimeRow row) {
        return new PluginRuntimeRecord(
                row.id(),
                row.installationId(),
                row.pluginId(),
                row.instanceId(),
                row.installationName(),
                row.version(),
                row.displayName(),
                row.mainClass(),
                row.artifactPath(),
                row.artifactSha256(),
                row.runtimeType(),
                PluginRuntimeState.valueOf(row.state()),
                row.runnerHost(),
                row.runnerPort(),
                row.workDir(),
                row.capabilitiesJson(),
                row.configSchemaJson(),
                row.accountSchemaJson(),
                row.dependencyJarsJson(),
                row.remoteDebugEnabled(),
                row.remoteDebugPort(),
                row.remoteDebugSuspend(),
                row.ownerUserId(),
                row.ownerUsername(),
                row.ownerDepartmentId(),
                row.ownerDepartmentName(),
                row.startedAt(),
                row.createdAt(),
                row.updatedAt()
        );
    }

    public int updateRemoteDebug(String installationId, boolean enabled, int port, boolean suspend) {
        return mapper.updateRemoteDebug(installationId, enabled, port, suspend);
    }

    public PluginRuntimeRecord createInstallation(String pluginId, String installationName, String ownerUserId, Long ownerDepartmentId) {
        String installationId = java.util.UUID.randomUUID().toString();
        Map<String, Object> values = new HashMap<>();
        values.put("installationId", installationId);
        values.put("pluginId", pluginId);
        values.put("instanceId", pluginId + "-" + installationId);
        values.put("installationName", installationName);
        values.put("ownerUserId", ownerUserId);
        values.put("ownerDepartmentId", ownerDepartmentId);
        mapper.insertInstallation(values);
        return findRuntimeByInstallationId(installationId).orElseThrow(() -> new IllegalStateException("Plugin installation was not saved"));
    }

    public List<String> notificationChannelIds(String installationId) {
        return mapper.notificationChannelIds(installationId);
    }

    public List<String> jobNotificationChannelIds(String installationId) {
        return mapper.jobNotificationChannelIds(installationId);
    }

    public void replaceNotificationChannels(String installationId, List<String> channelIds, String actor) {
        mapper.deleteNotificationChannels(installationId);
        for (String channelId : channelIds) mapper.insertNotificationChannel(installationId, channelId, actor);
    }

    public int transferOwnership(String installationId, String ownerUserId, Long ownerDepartmentId) {
        return mapper.transferOwnership(installationId, ownerUserId, ownerDepartmentId);
    }

    public Set<PluginCapability> runtimeCapabilities(String capabilitiesJson) {
        return capabilities(capabilitiesJson);
    }

    public List<Path> dependencyPaths(String dependencyJarsJson) {
        try {
            JsonNode root = objectMapper.readTree(dependencyJarsJson == null || dependencyJarsJson.isBlank() ? "[]" : dependencyJarsJson);
            if (!root.isArray()) {
                throw new IllegalArgumentException("Plugin dependency metadata must be an array");
            }
            Path repositoryRoot = dependencyProperties.repositoryRoot().toAbsolutePath().normalize();
            List<Path> paths = new java.util.ArrayList<>();
            for (JsonNode dependency : root) {
                String relativePath = dependency.path("relativePath").asText("");
                if (relativePath.isBlank()) {
                    throw new IllegalArgumentException("Plugin dependency path is missing");
                }
                Path path = repositoryRoot.resolve(relativePath).normalize();
                if (!path.startsWith(repositoryRoot) || !Files.isRegularFile(path) || !path.getFileName().toString().endsWith(".jar")) {
                    throw new IllegalArgumentException("Plugin dependency jar is not available: " + relativePath);
                }
                paths.add(path);
            }
            return List.copyOf(paths);
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid plugin dependency metadata", exception);
        }
    }

    private Set<PluginCapability> capabilities(String capabilitiesJson) {
        try {
            JsonNode root = objectMapper.readTree(capabilitiesJson == null || capabilitiesJson.isBlank() ? "{}" : capabilitiesJson);
            EnumSet<PluginCapability> capabilities = EnumSet.noneOf(PluginCapability.class);
            if (root.path("task").asBoolean(false)) capabilities.add(PluginCapability.TASK);
            if (root.path("config").asBoolean(false)) capabilities.add(PluginCapability.CONFIG);
            if (root.path("account").asBoolean(false)) capabilities.add(PluginCapability.ACCOUNT);
            if (root.path("notification").asBoolean(false)) capabilities.add(PluginCapability.NOTIFICATION);
            if (root.path("service").asBoolean(false)) capabilities.add(PluginCapability.SERVICE);
            return Set.copyOf(capabilities);
        } catch (Exception exception) {
            throw new IllegalArgumentException("Invalid plugin runtime capabilities", exception);
        }
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }
}
