package com.sky.job.plugin.runtime;

import com.sky.job.scheduler.PluginRuntimeMetadataStore;
import com.sky.job.system.DynamicProcessPluginRuntimeSettings;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PluginRuntimeLifecycleGuard {
    private static final Logger log = LoggerFactory.getLogger(PluginRuntimeLifecycleGuard.class);

    private final ManagedPluginRuntimeHandleRegistry registry;
    private final PluginRuntimeAdapter runtimeAdapter;
    private final PluginRuntimeMetadataStore runtimeStore;
    private final DynamicProcessPluginRuntimeSettings runtimeSettings;

    public PluginRuntimeLifecycleGuard(
            ManagedPluginRuntimeHandleRegistry registry,
            PluginRuntimeAdapter runtimeAdapter,
            PluginRuntimeMetadataStore runtimeStore,
            DynamicProcessPluginRuntimeSettings runtimeSettings
    ) {
        this.registry = registry;
        this.runtimeAdapter = runtimeAdapter;
        this.runtimeStore = runtimeStore;
        this.runtimeSettings = runtimeSettings;
    }

    @PostConstruct
    public void resetStaleRuntimeState() {
        int updated = runtimeStore.stopStaleRunningRuntimes();
        if (updated > 0) {
            log.info("Reset stale plugin runtime states on host startup: count={}", updated);
        }
    }

    @PreDestroy
    public void stopManagedRuntimes() {
        for (PluginRuntimeHandle handle : registry.snapshot()) {
            registry.remove(handle.pluginId()).ifPresent(this::stopRuntime);
        }
    }

    private void stopRuntime(PluginRuntimeHandle handle) {
        try {
            runtimeAdapter.stop(handle, runtimeSettings.stopTimeout());
            runtimeStore.updateLifecycle(handle.pluginId(), PluginRuntimeState.STOPPED, "127.0.0.1", null, null);
        } catch (RuntimeException exception) {
            log.warn("Failed to stop plugin runtime during host shutdown: pluginId={}, instanceId={}", handle.pluginId(), handle.instanceId(), exception);
        }
    }
}
