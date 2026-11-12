package com.sky.job.plugin.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.HostNotificationEvent;
import com.sky.job.notification.NotificationEventType;
import com.sky.job.notification.NotificationService;
import com.sky.job.notification.NotificationChannelStore;
import com.sky.job.plugin.artifact.PluginArtifactRegistry;
import com.sky.job.plugin.runtime.dto.PluginOptionResponse;
import com.sky.job.plugin.runtime.dto.PluginRuntimeResponse;
import com.sky.job.plugin.runtime.dto.UpdatePluginRuntimeDebugRequest;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginAccountGroupStore;
import com.sky.job.plugin.storage.PluginAccountListPreferenceStore;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginDataStore;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.EffectiveDataScope;
import com.sky.job.security.PermissionNames;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import com.sky.job.web.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

@Service
public class PluginRuntimeService {
    private static final Logger log = LoggerFactory.getLogger(PluginRuntimeService.class);
    private static final String RUNNER_HOST = "127.0.0.1";

    private final PluginRuntimeMetadataStore store;
    private final PluginArtifactRegistry artifactRegistry;
    private final DataScopeService dataScopeService;
    private final ManagedPluginRuntimeHandleRegistry registry;
    private final PluginRuntimeAdapter runtimeAdapter;
    private final ProcessPluginRuntimeProperties runtimeProperties;
    private final DynamicProcessPluginRuntimeSettings runtimeSettings;
    private final JobStore jobStore;
    private final PluginConfigStore configStore;
    private final PluginAccountStore accountStore;
    private final PluginAccountGroupStore accountGroupStore;
    private final PluginAccountListPreferenceStore accountListPreferenceStore;
    private final PluginDataStore dataStore;
    private final PluginAuditStore auditStore;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final int hostGrpcPort;
    private final NotificationChannelStore notificationChannelStore;

    public PluginRuntimeService(
            PluginRuntimeMetadataStore store,
            PluginArtifactRegistry artifactRegistry,
            DataScopeService dataScopeService,
            ManagedPluginRuntimeHandleRegistry registry,
            PluginRuntimeAdapter runtimeAdapter,
            ProcessPluginRuntimeProperties runtimeProperties,
            DynamicProcessPluginRuntimeSettings runtimeSettings,
            JobStore jobStore,
            PluginConfigStore configStore,
            PluginAccountStore accountStore,
            PluginAccountGroupStore accountGroupStore,
            PluginAccountListPreferenceStore accountListPreferenceStore,
            PluginDataStore dataStore,
            PluginAuditStore auditStore,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            NotificationChannelStore notificationChannelStore,
            @Value("${sky.plugin.host-grpc.port}") int hostGrpcPort
    ) {
        this.store = store;
        this.artifactRegistry = artifactRegistry;
        this.dataScopeService = dataScopeService;
        this.registry = registry;
        this.runtimeAdapter = runtimeAdapter;
        this.runtimeProperties = runtimeProperties;
        this.runtimeSettings = runtimeSettings;
        this.jobStore = jobStore;
        this.configStore = configStore;
        this.accountStore = accountStore;
        this.accountGroupStore = accountGroupStore;
        this.accountListPreferenceStore = accountListPreferenceStore;
        this.dataStore = dataStore;
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.notificationChannelStore = notificationChannelStore;
        this.hostGrpcPort = hostGrpcPort;
    }

    public PluginRuntimeResponse create(com.sky.job.plugin.runtime.dto.CreatePluginInstallationRequest request, AuthenticatedUser user) {
        if (request == null) throw new IllegalArgumentException("Plugin installation request is required");
        String pluginId = required(request.pluginId(), "Plugin id is required");
        String installationName = required(request.installationName(), "Installation name is required");
        artifactRegistry.find(pluginId).orElseThrow(() -> new ResourceNotFoundException("Plugin definition not found"));
        if (user.departmentId() == null) throw new IllegalStateException("Current user has no department");
        PluginRuntimeRecord created = store.createInstallation(pluginId, installationName, user.userId(), user.departmentId());
        audit(created.installationId(), created.pluginId(), created.instanceId(), user.username(), "PLUGIN_INSTALLATION_CREATE", Map.of(
                "installationName", created.installationName(),
                "ownerUserId", created.ownerUserId(),
                "ownerDepartmentId", created.ownerDepartmentId()
        ));
        return response(created);
    }

    public PageResponse<PluginRuntimeResponse> list(String pluginId, PluginRuntimeState state, AuthenticatedUser user, Integer page, Integer size) {
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.PLUGIN_RUNTIME_READ);
        List<PluginRuntimeResponse> items = store.listRuntimes(pluginId, state, scope, page, size).stream().map(this::response).toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), store.countRuntimes(pluginId, state, scope));
    }

    public PageResponse<PluginOptionResponse> options(Integer page, Integer size, String keyword, PluginRuntimeState state, PluginOptionUsage usage, AuthenticatedUser user) {
        EffectiveDataScope scope = dataScopeService.resolve(user, PermissionNames.PLUGIN_RUNTIME_READ);
        List<PluginOptionResponse> items = store.listOptions(keyword, state, usage, scope, page, size).stream().map(this::option).toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), store.countOptions(keyword, state, usage, scope));
    }

    public PluginRuntimeResponse detail(String installationId, AuthenticatedUser user) {
        return response(scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_READ));
    }

    public synchronized PluginRuntimeResponse start(String installationId, AuthenticatedUser user) {
        scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_MANAGE);
        return startInternal(installationId, user.username(), true, true);
    }

    public synchronized PluginRuntimeHandle ensureRunningHandle(String installationId, String actor) {
        startInternal(installationId, actor, false, true);
        return registry.get(installationId).orElseThrow(() -> new IllegalStateException("Plugin runtime is not running"));
    }

    public synchronized PluginRuntimeResponse stop(String installationId, AuthenticatedUser user) {
        scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_MANAGE);
        return stopInternal(installationId, user.username(), true);
    }

    public synchronized PluginRuntimeResponse restart(String installationId, AuthenticatedUser user) {
        PluginRuntimeRecord before = scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_MANAGE);
        stopInternal(installationId, user.username(), false);
        PluginRuntimeResponse response = startInternal(installationId, user.username(), false, true);
        auditRuntimeRestart(before, response, user.username());
        return response;
    }

    public PluginRuntimeResponse updateDebug(String installationId, UpdatePluginRuntimeDebugRequest request, AuthenticatedUser user) {
        PluginRuntimeRecord record = scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_MANAGE);
        validateDebugRequest(request);
        if (store.updateRemoteDebug(installationId, request.enabled(), request.port(), request.suspend()) == 0) {
            throw new ResourceNotFoundException("Plugin installation not found");
        }
        auditRuntimeDebugUpdate(record, request, user.username());
        return response(record(installationId));
    }

    @Transactional
    public List<String> replaceNotificationChannels(String installationId, com.sky.job.plugin.runtime.dto.ReplaceNotificationChannelsRequest request, AuthenticatedUser user) {
        scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_MANAGE);
        requirePermissions(user, PermissionNames.PLUGIN_RUNTIME_MANAGE, PermissionNames.PLUGIN_NOTIFICATION_CHANNEL_BIND, PermissionNames.NOTIFICATION_CHANNEL_READ);
        List<String> channelIds = request == null ? List.of() : request.channelIds().stream().distinct().toList();
        EffectiveDataScope channelScope = dataScopeService.resolve(user, PermissionNames.NOTIFICATION_CHANNEL_READ);
        for (String channelId : channelIds) {
            notificationChannelStore.findByChannelId(channelId, channelScope)
                    .orElseThrow(() -> new ResourceNotFoundException("Notification channel not found"));
        }
        java.util.Set<String> replacementPool = java.util.Set.copyOf(channelIds);
        List<String> orphanedJobChannels = store.jobNotificationChannelIds(installationId).stream()
                .filter(channelId -> !replacementPool.contains(channelId))
                .toList();
        if (!orphanedJobChannels.isEmpty()) {
            throw new IllegalArgumentException("Channels are still bound to jobs and must be unbound first: " + orphanedJobChannels);
        }
        store.replaceNotificationChannels(installationId, channelIds, user.username());
        return store.notificationChannelIds(installationId);
    }

    public List<String> notificationChannelIds(String installationId, AuthenticatedUser user) {
        scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_MANAGE);
        requirePermissions(user, PermissionNames.PLUGIN_RUNTIME_MANAGE, PermissionNames.PLUGIN_NOTIFICATION_CHANNEL_BIND, PermissionNames.NOTIFICATION_CHANNEL_READ);
        return store.notificationChannelIds(installationId);
    }

    @Transactional
    public synchronized void uninstall(String installationId, AuthenticatedUser user) {
        PluginRuntimeRecord record = scopedRecord(installationId, user, PermissionNames.PLUGIN_RUNTIME_MANAGE);
        if (jobStore.existsByPluginInstallationId(installationId)) {
            throw new IllegalArgumentException("Cannot uninstall a plugin with jobs");
        }
        Optional<PluginRuntimeHandle> handle = registry.remove(installationId);
        handle.ifPresent(runtimeHandle -> runtimeAdapter.stop(runtimeHandle, runtimeSettings.stopTimeout()));
        configStore.deleteByInstallationId(installationId);
        accountStore.deleteByInstallationId(installationId);
        accountGroupStore.deleteByInstallationId(installationId);
        accountListPreferenceStore.deleteByInstallationId(installationId);
        dataStore.deleteByInstallationId(installationId);
        store.softDelete(installationId);
        deleteWorkDir(record.workDir());
        auditRuntimeUninstall(record, user.username(), handle.isPresent());
    }

    private PluginRuntimeResponse startInternal(String installationId, String actor, boolean auditSuccess, boolean auditFailure) {
        PluginRuntimeRecord record = record(installationId);
        PluginRuntimeState previousState = record.state();
        var existing = registry.get(installationId);
        if (existing.isPresent() && runtimeAdapter.inspect(existing.get()) == PluginRuntimeState.RUNNING) {
            PluginRuntimeResponse response = response(record(installationId));
            if (auditSuccess) {
                auditRuntimeStart(record, actor, workDir(record), response.runnerPort(), previousState);
            }
            return response;
        }
        existing.ifPresent(handle -> registry.remove(installationId));
        validate(record);
        Path workDir = workDir(record);
        store.updateLifecycle(installationId, PluginRuntimeState.STARTING, RUNNER_HOST, null, workDir);
        try {
            PluginRuntimeHandle handle = runtimeAdapter.start(new PluginRuntimeSpec(
                    record.installationId(),
                    record.pluginId(),
                    record.instanceId(),
                    record.mainClass(),
                    Path.of(record.artifactPath()),
                    record.artifactSha256(),
                    workDir,
                    hostGrpcPort,
                    store.dependencyPaths(record.dependencyJarsJson()),
                    store.runtimeCapabilities(record.capabilitiesJson()),
                    record.remoteDebugEnabled(),
                    record.remoteDebugPort(),
                    record.remoteDebugSuspend(),
                    null,
                    null,
                    null
            ));
            registry.put(handle);
            store.updateLifecycle(installationId, PluginRuntimeState.RUNNING, RUNNER_HOST, handle.port(), workDir);
            if (auditSuccess) {
                auditRuntimeStart(record, actor, workDir, handle.port(), previousState);
            }
            return response(record(installationId));
        } catch (RuntimeException exception) {
            store.updateLifecycle(installationId, PluginRuntimeState.FAILED, RUNNER_HOST, null, workDir);
            if (auditFailure) {
                auditRuntimeStartFailed(record, actor, workDir, previousState, exception);
            }
            sendRuntimeStartFailedAlert(record, previousState, exception);
            throw exception;
        }
    }

    private PluginRuntimeResponse stopInternal(String installationId, String actor, boolean auditSuccess) {
        PluginRuntimeRecord record = record(installationId);
        PluginRuntimeState previousState = record.state();
        Integer previousRunnerPort = record.runnerPort();
        Optional<PluginRuntimeHandle> handle = registry.remove(installationId);
        handle.ifPresent(runtimeHandle -> runtimeAdapter.stop(runtimeHandle, runtimeSettings.stopTimeout()));
        Path workDir = record.workDir() == null || record.workDir().isBlank() ? null : Path.of(record.workDir());
        store.updateLifecycle(installationId, PluginRuntimeState.STOPPED, record.runnerHost(), null, workDir);
        if (auditSuccess) {
            auditRuntimeStop(record, actor, previousState, previousRunnerPort, handle.isPresent());
        }
        return response(record(installationId));
    }

    private void sendRuntimeStartFailedAlert(PluginRuntimeRecord record, PluginRuntimeState previousState, RuntimeException exception) {
        Map<String, String> labels = Map.of(
                "eventType", NotificationEventType.PLUGIN_RUNTIME_START_FAILED.name(),
                "pluginId", record.pluginId(),
                "previousState", previousState.name(),
                "state", PluginRuntimeState.FAILED.name()
        );
        String content = "Plugin runtime start failed: pluginId=" + record.pluginId()
                + ", instanceId=" + record.instanceId()
                + ", previousState=" + previousState.name()
                + ", runtimeType=" + record.runtimeType()
                + ", errorType=" + exception.getClass().getName()
                + ", errorMessage=" + errorMessage(exception);
        notificationService.sendHostAlert(new HostNotificationEvent(
                NotificationEventType.PLUGIN_RUNTIME_START_FAILED, "Plugin runtime start failed", content, "ERROR",
                null, record.installationId(), record.pluginId(), record.instanceId(), "",
                record.ownerUserId(), record.ownerDepartmentId(), labels));
    }

    private void auditRuntimeStart(PluginRuntimeRecord record, String actor, Path workDir, Integer runnerPort, PluginRuntimeState previousState) {
        Map<String, Object> detail = baseRuntimeDetail(record, previousState, PluginRuntimeState.RUNNING);
        detail.put("runnerHost", RUNNER_HOST);
        detail.put("runnerPort", runnerPort);
        detail.put("workDir", pathString(workDir));
        audit(record.installationId(), record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_START", detail);
    }

    private void auditRuntimeStartFailed(PluginRuntimeRecord record, String actor, Path workDir, PluginRuntimeState previousState, RuntimeException exception) {
        Map<String, Object> detail = baseRuntimeDetail(record, previousState, PluginRuntimeState.FAILED);
        detail.put("runnerHost", RUNNER_HOST);
        detail.put("workDir", pathString(workDir));
        detail.put("errorType", exception.getClass().getName());
        detail.put("errorMessage", errorMessage(exception));
        audit(record.installationId(), record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_START_FAILED", detail);
    }

    private void auditRuntimeStop(PluginRuntimeRecord record, String actor, PluginRuntimeState previousState, Integer previousRunnerPort, boolean hadManagedHandle) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousState", previousState.name());
        detail.put("state", PluginRuntimeState.STOPPED.name());
        detail.put("runnerHost", record.runnerHost());
        detail.put("previousRunnerPort", previousRunnerPort);
        detail.put("workDir", record.workDir() == null ? "" : record.workDir());
        detail.put("hadManagedHandle", hadManagedHandle);
        audit(record.installationId(), record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_STOP", detail);
    }

    private void auditRuntimeUninstall(PluginRuntimeRecord record, String actor, boolean hadManagedHandle) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousState", record.state().name());
        detail.put("runtimeType", record.runtimeType());
        detail.put("artifactSha256", record.artifactSha256());
        detail.put("artifactPath", record.artifactPath());
        detail.put("workDir", record.workDir() == null ? "" : record.workDir());
        detail.put("hadManagedHandle", hadManagedHandle);
        audit(record.installationId(), record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_UNINSTALL", detail);
    }

    private void auditRuntimeRestart(PluginRuntimeRecord before, PluginRuntimeResponse response, String actor) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousState", before.state().name());
        detail.put("state", response.state().name());
        detail.put("runnerHost", response.runnerHost());
        detail.put("runnerPort", response.runnerPort());
        detail.put("workDir", response.workDir());
        detail.put("runtimeType", before.runtimeType());
        detail.put("artifactSha256", before.artifactSha256());
        audit(response.installationId(), response.pluginId(), response.instanceId(), actor, "PLUGIN_RUNTIME_RESTART", detail);
    }

    private void auditRuntimeDebugUpdate(PluginRuntimeRecord record, UpdatePluginRuntimeDebugRequest request, String actor) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousEnabled", record.remoteDebugEnabled());
        detail.put("previousPort", record.remoteDebugPort());
        detail.put("previousSuspend", record.remoteDebugSuspend());
        detail.put("enabled", request.enabled());
        detail.put("port", request.port());
        detail.put("suspend", request.suspend());
        audit(record.installationId(), record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_DEBUG_UPDATE", detail);
    }

    private Map<String, Object> baseRuntimeDetail(PluginRuntimeRecord record, PluginRuntimeState previousState, PluginRuntimeState state) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousState", previousState.name());
        detail.put("state", state.name());
        detail.put("runtimeType", record.runtimeType());
        detail.put("artifactSha256", record.artifactSha256());
        return detail;
    }

    private void audit(String installationId, String pluginId, String instanceId, String actor, String action, Map<String, ?> detail) {
        try {
            auditStore.insert(installationId, pluginId, instanceId, actor, action, objectMapper.writeValueAsString(detail));
        } catch (JsonProcessingException exception) {
            log.warn("Failed to serialize plugin runtime audit detail: pluginId={}, instanceId={}, action={}", pluginId, instanceId, action, exception);
        } catch (RuntimeException exception) {
            log.warn("Failed to write plugin runtime audit log: pluginId={}, instanceId={}, action={}", pluginId, instanceId, action, exception);
        }
    }

    private String pathString(Path path) {
        return path == null ? "" : path.toAbsolutePath().normalize().toString();
    }

    private String errorMessage(RuntimeException exception) {
        return exception.getMessage() == null ? "" : exception.getMessage();
    }

    private PluginRuntimeResponse response(PluginRuntimeRecord record) {
        PluginRuntimeState liveState = registry.get(record.installationId()).map(runtimeAdapter::inspect).orElse(PluginRuntimeState.STOPPED);
        boolean managedByCurrentHost = registry.get(record.installationId()).isPresent();
        return PluginRuntimeResponse.from(record, managedByCurrentHost, liveState);
    }

    private PluginOptionResponse option(PluginRuntimeRecord record) {
        return new PluginOptionResponse(record.installationId(), record.pluginId(), record.installationName(), record.displayName(), record.version(), record.ownerUserId(), record.ownerDepartmentId(), record.state());
    }

    private PluginRuntimeRecord record(String installationId) {
        return store.findRuntimeByInstallationId(installationId).orElseThrow(() -> new ResourceNotFoundException("Plugin installation not found"));
    }

    private PluginRuntimeRecord scopedRecord(String installationId, AuthenticatedUser user, String permissionName) {
        PluginRuntimeRecord record = record(installationId);
        if (!dataScopeService.allows(dataScopeService.resolve(user, permissionName), record.ownerUserId(), record.ownerDepartmentId())) {
            throw new ResourceNotFoundException("Plugin installation not found");
        }
        return record;
    }

    private void validate(PluginRuntimeRecord record) {
        if (!Objects.equals(record.runtimeType(), "process")) {
            throw new IllegalArgumentException("Plugin runtime must use process type");
        }
        if (record.artifactPath() == null || record.artifactPath().isBlank() || record.artifactSha256() == null || record.artifactSha256().isBlank()) {
            throw new IllegalArgumentException("Plugin runtime artifact metadata is incomplete");
        }
        Set<?> capabilities = store.runtimeCapabilities(record.capabilitiesJson());
        if (capabilities.isEmpty()) {
            throw new IllegalArgumentException("Plugin runtime capabilities are incomplete");
        }
    }

    private void validateDebugRequest(UpdatePluginRuntimeDebugRequest request) {
        if (request == null || request.enabled() == null || request.port() == null || request.suspend() == null) {
            throw new IllegalArgumentException("Plugin runtime debug settings are required");
        }
        if (request.port() < 1 || request.port() > 65535) {
            throw new IllegalArgumentException("Plugin runtime debug port is out of range");
        }
    }

    private Path workDir(PluginRuntimeRecord record) {
        if (record.workDir() != null && !record.workDir().isBlank()) {
            return Path.of(record.workDir());
        }
        return runtimeProperties.workRoot().resolve(record.pluginId()).resolve(record.installationId()).resolve(record.instanceId());
    }

    private void deleteArtifact(String artifactPath) {
        if (artifactPath == null || artifactPath.isBlank()) {
            return;
        }
        Path artifact = requireUnderRoot(Path.of(artifactPath), runtimeProperties.artifactRoot(), "artifact");
        deleteFileIfExists(artifact);
    }

    private void deleteWorkDir(String workDir) {
        if (workDir == null || workDir.isBlank()) {
            return;
        }
        Path path = requireUnderRoot(Path.of(workDir), runtimeProperties.workRoot(), "work directory");
        deleteDirectoryIfExists(path);
    }

    private Path requireUnderRoot(Path candidate, Path root, String label) {
        Path normalizedRoot = root.toAbsolutePath().normalize();
        Path normalizedCandidate = candidate.toAbsolutePath().normalize();
        if (!normalizedCandidate.startsWith(normalizedRoot)) {
            throw new IllegalArgumentException("Plugin " + label + " escapes configured root");
        }
        return normalizedCandidate;
    }

    private void deleteFileIfExists(Path path) {
        try {
            Files.deleteIfExists(path);
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete plugin artifact file", exception);
        }
    }

    private void deleteDirectoryIfExists(Path path) {
        if (!Files.exists(path)) {
            return;
        }
        try (var paths = Files.walk(path)) {
            for (Path child : paths.sorted(java.util.Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(child);
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to delete plugin work directory", exception);
        }
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }

    private String required(String value, String message) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(message);
        return value.trim();
    }

    private void requirePermissions(AuthenticatedUser user, String... permissionNames) {
        for (String permissionName : permissionNames) {
            if (!user.roles().contains("ADMIN") && !user.permissions().contains(permissionName)) {
                throw new org.springframework.security.access.AccessDeniedException("Missing permission: " + permissionName);
            }
        }
    }
}
