package com.sky.job.plugin.runtime;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ServicePluginIdleTracker {
    private final ConcurrentMap<String, Instant> lastCallTimes = new ConcurrentHashMap<>();

    public void markCalled(String pluginId) {
        if (pluginId != null && !pluginId.isBlank()) {
            lastCallTimes.put(pluginId, Instant.now());
        }
    }

    public Optional<Instant> lastCalledAt(String pluginId) {
        return Optional.ofNullable(lastCallTimes.get(pluginId));
    }

    public void remove(String pluginId) {
        lastCallTimes.remove(pluginId);
    }
}
