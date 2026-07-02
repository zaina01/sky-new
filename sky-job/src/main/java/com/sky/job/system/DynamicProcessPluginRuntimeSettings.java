package com.sky.job.system;

import org.springframework.stereotype.Component;

import java.time.Duration;

@Component
public class DynamicProcessPluginRuntimeSettings {
    private final SystemSettingProvider provider;

    public DynamicProcessPluginRuntimeSettings(SystemSettingProvider provider) {
        this.provider = provider;
    }

    public Duration startupTimeout() {
        return provider.getDuration(SystemSettingDefinitions.PLUGIN_RUNTIME_STARTUP_TIMEOUT);
    }

    public Duration stopTimeout() {
        return provider.getDuration(SystemSettingDefinitions.PLUGIN_RUNTIME_STOP_TIMEOUT);
    }

    public Duration taskTimeout() {
        return provider.getDuration(SystemSettingDefinitions.PLUGIN_RUNTIME_TASK_TIMEOUT);
    }

    public Duration serviceIdleTimeout() {
        return provider.getDuration(SystemSettingDefinitions.PLUGIN_RUNTIME_SERVICE_IDLE_TIMEOUT);
    }

    public Duration serviceIdleScanInterval() {
        return provider.getDuration(SystemSettingDefinitions.PLUGIN_RUNTIME_SERVICE_IDLE_SCAN_INTERVAL);
    }

    public int maxRestarts() {
        return provider.getInt(SystemSettingDefinitions.PLUGIN_RUNTIME_MAX_RESTARTS);
    }

    public String maxHeap() {
        return provider.getString(SystemSettingDefinitions.PLUGIN_RUNTIME_MAX_HEAP);
    }
}
