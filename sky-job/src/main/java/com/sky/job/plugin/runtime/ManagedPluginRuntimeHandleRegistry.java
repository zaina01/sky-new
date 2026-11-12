package com.sky.job.plugin.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ManagedPluginRuntimeHandleRegistry {
    private final ConcurrentMap<String, PluginRuntimeHandle> handles = new ConcurrentHashMap<>();

    public Optional<PluginRuntimeHandle> get(String installationId) {
        return Optional.ofNullable(handles.get(installationId));
    }

    public void put(PluginRuntimeHandle handle) {
        handles.put(handle.installationId(), handle);
    }

    public Optional<PluginRuntimeHandle> remove(String installationId) {
        return Optional.ofNullable(handles.remove(installationId));
    }

    public List<PluginRuntimeHandle> snapshot() {
        return List.copyOf(handles.values());
    }
}
