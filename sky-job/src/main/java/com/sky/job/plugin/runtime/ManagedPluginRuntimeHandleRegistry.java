package com.sky.job.plugin.runtime;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ManagedPluginRuntimeHandleRegistry {
    private final ConcurrentMap<String, PluginRuntimeHandle> handles = new ConcurrentHashMap<>();

    public Optional<PluginRuntimeHandle> get(String pluginId) {
        return Optional.ofNullable(handles.get(pluginId));
    }

    public void put(PluginRuntimeHandle handle) {
        handles.put(handle.pluginId(), handle);
    }

    public Optional<PluginRuntimeHandle> remove(String pluginId) {
        return Optional.ofNullable(handles.remove(pluginId));
    }

    public List<PluginRuntimeHandle> snapshot() {
        return List.copyOf(handles.values());
    }
}
