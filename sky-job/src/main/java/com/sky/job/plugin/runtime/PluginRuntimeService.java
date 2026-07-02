package com.sky.job.plugin.runtime;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sky.job.notification.HostNotificationEvent;
import com.sky.job.notification.NotificationEventType;
import com.sky.job.notification.NotificationService;
import com.sky.job.plugin.runtime.dto.PluginOptionResponse;
import com.sky.job.plugin.runtime.dto.PluginRuntimeResponse;
import com.sky.job.plugin.runtime.process.ProcessPluginRuntimeProperties;
import com.sky.job.plugin.storage.PluginAccountStore;
import com.sky.job.plugin.storage.PluginAuditStore;
import com.sky.job.plugin.storage.PluginConfigStore;
import com.sky.job.plugin.storage.PluginDataStore;
import com.sky.job.scheduler.JobStore;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.scheduler.dto.PageResponse;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
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
    private final ManagedPluginRuntimeHandleRegistry registry;
    private final PluginRuntimeAdapter runtimeAdapter;
    private final ProcessPluginRuntimeProperties runtimeProperties;
    private final DynamicProcessPluginRuntimeSettings runtimeSettings;
    private final JobStore jobStore;
    private final PluginConfigStore configStore;
    private final PluginAccountStore accountStore;
    private final PluginDataStore dataStore;
    private final PluginAuditStore auditStore;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;
    private final int hostGrpcPort;

    public PluginRuntimeService(
            PluginRuntimeMetadataStore store,
            ManagedPluginRuntimeHandleRegistry registry,
            PluginRuntimeAdapter runtimeAdapter,
            ProcessPluginRuntimeProperties runtimeProperties,
            DynamicProcessPluginRuntimeSettings runtimeSettings,
            JobStore jobStore,
            PluginConfigStore configStore,
            PluginAccountStore accountStore,
            PluginDataStore dataStore,
            PluginAuditStore auditStore,
            ObjectMapper objectMapper,
            NotificationService notificationService,
            @Value("${sky.plugin.host-grpc.port}") int hostGrpcPort
    ) {
        this.store = store;
        this.registry = registry;
        this.runtimeAdapter = runtimeAdapter;
        this.runtimeProperties = runtimeProperties;
        this.runtimeSettings = runtimeSettings;
        this.jobStore = jobStore;
        this.configStore = configStore;
        this.accountStore = accountStore;
        this.dataStore = dataStore;
        this.auditStore = auditStore;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
        this.hostGrpcPort = hostGrpcPort;
    }

    public PageResponse<PluginRuntimeResponse> list(String pluginId, PluginRuntimeState state, Integer page, Integer size) {
        List<PluginRuntimeResponse> items = store.listRuntimes(pluginId, state, page, size).stream().map(this::response).toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), store.countRuntimes(pluginId, state));
    }

    public PageResponse<PluginOptionResponse> options(Integer page, Integer size, String keyword, PluginRuntimeState state, PluginOptionUsage usage) {
        List<PluginOptionResponse> items = store.listOptions(keyword, state, usage, page, size).stream().map(this::option).toList();
        return new PageResponse<>(items, normalizedPage(page), normalizedSize(size), store.countOptions(keyword, state, usage));
    }

    public PluginRuntimeResponse detail(String pluginId) {
        return response(record(pluginId));
    }

    public synchronized PluginRuntimeResponse start(String pluginId, String actor) {
        return start(pluginId, actor, true, true);
    }

    public synchronized PluginRuntimeHandle ensureRunningHandle(String pluginId, String actor) {
        start(pluginId, actor, false, true);
        return registry.get(pluginId).orElseThrow(() -> new IllegalStateException("Plugin runtime is not running"));
    }

    public synchronized PluginRuntimeResponse stop(String pluginId, String actor) {
        return stop(pluginId, actor, true);
    }

    public synchronized PluginRuntimeResponse restart(String pluginId, String actor) {
        PluginRuntimeRecord before = record(pluginId);
        stop(pluginId, actor, false);
        PluginRuntimeResponse response = start(pluginId, actor, false, true);
        auditRuntimeRestart(before, response, actor);
        return response;
    }

    @Transactional
    public synchronized void uninstall(String pluginId, String actor) {
        PluginRuntimeRecord record = record(pluginId);
        if (jobStore.existsByPluginId(pluginId)) {
            throw new IllegalArgumentException("Cannot uninstall a plugin with jobs");
        }
        Optional<PluginRuntimeHandle> handle = registry.remove(pluginId);
        handle.ifPresent(runtimeHandle -> runtimeAdapter.stop(runtimeHandle, runtimeSettings.stopTimeout()));
        configStore.deleteByPluginId(pluginId);
        accountStore.deleteByPluginId(pluginId);
        dataStore.deleteByPluginId(pluginId);
        store.deleteByPluginId(pluginId);
        deleteArtifact(record.artifactPath());
        deleteWorkDir(record.workDir());
        auditRuntimeUninstall(record, actor, handle.isPresent());
    }

    private PluginRuntimeResponse start(String pluginId, String actor, boolean auditSuccess, boolean auditFailure) {
        PluginRuntimeRecord record = record(pluginId);
        PluginRuntimeState previousState = record.state();
        var existing = registry.get(pluginId);
        if (existing.isPresent() && runtimeAdapter.inspect(existing.get()) == PluginRuntimeState.RUNNING) {
            PluginRuntimeResponse response = detail(pluginId);
            if (auditSuccess) {
                auditRuntimeStart(record, actor, workDir(record), response.runnerPort(), previousState);
            }
            return response;
        }
        existing.ifPresent(handle -> registry.remove(pluginId));
        validate(record);
        Path workDir = workDir(record);
        store.updateLifecycle(pluginId, PluginRuntimeState.STARTING, RUNNER_HOST, null, workDir);
        try {
            PluginRuntimeHandle handle = runtimeAdapter.start(new PluginRuntimeSpec(
                    record.pluginId(),
                    record.instanceId(),
                    record.mainClass(),
                    Path.of(record.artifactPath()),
                    record.artifactSha256(),
                    workDir,
                    hostGrpcPort,
                    store.dependencyPaths(record.dependencyJarsJson()),
                    store.runtimeCapabilities(record.capabilitiesJson()),
                    null
            ));
            registry.put(handle);
            store.updateLifecycle(pluginId, PluginRuntimeState.RUNNING, RUNNER_HOST, handle.port(), workDir);
            if (auditSuccess) {
                auditRuntimeStart(record, actor, workDir, handle.port(), previousState);
            }
            return detail(pluginId);
        } catch (RuntimeException exception) {
            store.updateLifecycle(pluginId, PluginRuntimeState.FAILED, RUNNER_HOST, null, workDir);
            if (auditFailure) {
                auditRuntimeStartFailed(record, actor, workDir, previousState, exception);
            }
            sendRuntimeStartFailedAlert(record, previousState, exception);
            throw exception;
        }
    }

    private PluginRuntimeResponse stop(String pluginId, String actor, boolean auditSuccess) {
        PluginRuntimeRecord record = record(pluginId);
        PluginRuntimeState previousState = record.state();
        Integer previousRunnerPort = record.runnerPort();
        Optional<PluginRuntimeHandle> handle = registry.remove(pluginId);
        handle.ifPresent(runtimeHandle -> runtimeAdapter.stop(runtimeHandle, runtimeSettings.stopTimeout()));
        Path workDir = record.workDir() == null || record.workDir().isBlank() ? null : Path.of(record.workDir());
        store.updateLifecycle(pluginId, PluginRuntimeState.STOPPED, record.runnerHost(), null, workDir);
        if (auditSuccess) {
            auditRuntimeStop(record, actor, previousState, previousRunnerPort, handle.isPresent());
        }
        return detail(pluginId);
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
        notificationService.sendHostAlert(new HostNotificationEvent(NotificationEventType.PLUGIN_RUNTIME_START_FAILED, "Plugin runtime start failed", content, "ERROR", null, record.pluginId(), record.instanceId(), "", labels));
    }

    private void auditRuntimeStart(PluginRuntimeRecord record, String actor, Path workDir, Integer runnerPort, PluginRuntimeState previousState) {
        Map<String, Object> detail = baseRuntimeDetail(record, previousState, PluginRuntimeState.RUNNING);
        detail.put("runnerHost", RUNNER_HOST);
        detail.put("runnerPort", runnerPort);
        detail.put("workDir", pathString(workDir));
        audit(record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_START", detail);
    }

    private void auditRuntimeStartFailed(PluginRuntimeRecord record, String actor, Path workDir, PluginRuntimeState previousState, RuntimeException exception) {
        Map<String, Object> detail = baseRuntimeDetail(record, previousState, PluginRuntimeState.FAILED);
        detail.put("runnerHost", RUNNER_HOST);
        detail.put("workDir", pathString(workDir));
        detail.put("errorType", exception.getClass().getName());
        detail.put("errorMessage", errorMessage(exception));
        audit(record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_START_FAILED", detail);
    }

    private void auditRuntimeStop(PluginRuntimeRecord record, String actor, PluginRuntimeState previousState, Integer previousRunnerPort, boolean hadManagedHandle) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousState", previousState.name());
        detail.put("state", PluginRuntimeState.STOPPED.name());
        detail.put("runnerHost", record.runnerHost());
        detail.put("previousRunnerPort", previousRunnerPort);
        detail.put("workDir", record.workDir() == null ? "" : record.workDir());
        detail.put("hadManagedHandle", hadManagedHandle);
        audit(record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_STOP", detail);
    }

    private void auditRuntimeUninstall(PluginRuntimeRecord record, String actor, boolean hadManagedHandle) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousState", record.state().name());
        detail.put("runtimeType", record.runtimeType());
        detail.put("artifactSha256", record.artifactSha256());
        detail.put("artifactPath", record.artifactPath());
        detail.put("workDir", record.workDir() == null ? "" : record.workDir());
        detail.put("hadManagedHandle", hadManagedHandle);
        audit(record.pluginId(), record.instanceId(), actor, "PLUGIN_RUNTIME_UNINSTALL", detail);
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
        audit(response.pluginId(), response.instanceId(), actor, "PLUGIN_RUNTIME_RESTART", detail);
    }

    private Map<String, Object> baseRuntimeDetail(PluginRuntimeRecord record, PluginRuntimeState previousState, PluginRuntimeState state) {
        Map<String, Object> detail = new LinkedHashMap<>();
        detail.put("previousState", previousState.name());
        detail.put("state", state.name());
        detail.put("runtimeType", record.runtimeType());
        detail.put("artifactSha256", record.artifactSha256());
        return detail;
    }

    private void audit(String pluginId, String instanceId, String actor, String action, Map<String, Object> detail) {
        try {
            auditStore.insert(pluginId, instanceId, actor, action, objectMapper.writeValueAsString(detail));
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
        PluginRuntimeState liveState = registry.get(record.pluginId()).map(runtimeAdapter::inspect).orElse(PluginRuntimeState.STOPPED);
        boolean managedByCurrentHost = registry.get(record.pluginId()).isPresent();
        return PluginRuntimeResponse.from(record, managedByCurrentHost, liveState);
    }

    private PluginOptionResponse option(PluginRuntimeRecord record) {
        return new PluginOptionResponse(record.pluginId(), record.displayName(), record.version(), record.state());
    }

    private PluginRuntimeRecord record(String pluginId) {
        return store.findRuntimeByPluginId(pluginId).orElseThrow(() -> new IllegalArgumentException("Plugin runtime not found"));
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

    private Path workDir(PluginRuntimeRecord record) {
        if (record.workDir() != null && !record.workDir().isBlank()) {
            return Path.of(record.workDir());
        }
        return runtimeProperties.workRoot().resolve(record.pluginId()).resolve(record.instanceId());
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
}
