package com.sky.job.plugin.runtime;

import com.sky.job.plugin.security.PluginCapability;
import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@Component
public class ServicePluginIdleRuntimeReaper {
    private static final Logger log = LoggerFactory.getLogger(ServicePluginIdleRuntimeReaper.class);

    private final ManagedPluginRuntimeHandleRegistry registry;
    private final PluginRuntimeAdapter runtimeAdapter;
    private final PluginRuntimeMetadataStore runtimeStore;
    private final ServicePluginIdleTracker idleTracker;
    private final DynamicProcessPluginRuntimeSettings runtimeSettings;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(task -> {
        Thread thread = new Thread(task, "service-plugin-idle-reaper");
        thread.setDaemon(true);
        return thread;
    });
    private final AtomicBoolean running = new AtomicBoolean(true);

    public ServicePluginIdleRuntimeReaper(
            ManagedPluginRuntimeHandleRegistry registry,
            PluginRuntimeAdapter runtimeAdapter,
            PluginRuntimeMetadataStore runtimeStore,
            ServicePluginIdleTracker idleTracker,
            DynamicProcessPluginRuntimeSettings runtimeSettings
    ) {
        this.registry = registry;
        this.runtimeAdapter = runtimeAdapter;
        this.runtimeStore = runtimeStore;
        this.idleTracker = idleTracker;
        this.runtimeSettings = runtimeSettings;
    }

    @PostConstruct
    public void start() {
        executor.execute(this::runLoop);
    }

    @PreDestroy
    public void stop() {
        running.set(false);
        executor.shutdownNow();
    }

    private void runLoop() {
        while (running.get()) {
            try {
                scan();
            } catch (Exception exception) {
                log.warn("Failed to reap idle service plugin runtimes", exception);
            }
            sleep(runtimeSettings.serviceIdleScanInterval());
        }
    }

    private void scan() {
        Duration idleTimeout = runtimeSettings.serviceIdleTimeout();
        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            return;
        }
        Instant now = Instant.now();
        for (PluginRuntimeHandle handle : registry.snapshot()) {
            tryStopIdle(handle, now, idleTimeout);
        }
    }

    private void tryStopIdle(PluginRuntimeHandle handle, Instant now, Duration idleTimeout) {
        if (runtimeAdapter.inspect(handle) != PluginRuntimeState.RUNNING) {
            registry.remove(handle.installationId());
            idleTracker.remove(handle.installationId());
            return;
        }
        PluginRuntimeRecord record = runtimeStore.findRuntimeByInstallationId(handle.installationId()).orElse(null);
        if (record == null || !runtimeStore.runtimeCapabilities(record.capabilitiesJson()).contains(PluginCapability.SERVICE)) {
            return;
        }
        Instant lastCalledAt = idleTracker.lastCalledAt(handle.installationId()).orElse(null);
        if (lastCalledAt == null || Duration.between(lastCalledAt, now).compareTo(idleTimeout) < 0) {
            return;
        }
        registry.remove(handle.installationId()).ifPresent(runtimeHandle -> {
            log.info("Stopping idle service plugin runtime: installationId={}, pluginId={}, instanceId={}, idleMillis={}", runtimeHandle.installationId(), runtimeHandle.pluginId(), runtimeHandle.instanceId(), Duration.between(lastCalledAt, now).toMillis());
            runtimeAdapter.stop(runtimeHandle, runtimeSettings.stopTimeout());
            idleTracker.remove(runtimeHandle.installationId());
            runtimeStore.updateLifecycle(runtimeHandle.installationId(), PluginRuntimeState.STOPPED, record.runnerHost(), null, record.workDir() == null || record.workDir().isBlank() ? null : java.nio.file.Path.of(record.workDir()));
        });
    }

    private void sleep(Duration interval) {
        long millis = Math.max(interval == null ? 60000 : interval.toMillis(), 1000);
        try {
            TimeUnit.MILLISECONDS.sleep(millis);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            running.set(false);
        }
    }
}
