package com.sky.job.plugin.runtime;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class ServicePluginIdleTracker {
    private final ConcurrentMap<String, Instant> lastCallTimes = new ConcurrentHashMap<>();

    public void markCalled(String installationId) {
        if (installationId != null && !installationId.isBlank()) {
            lastCallTimes.put(installationId, Instant.now());
        }
    }

    public Optional<Instant> lastCalledAt(String installationId) {
        return Optional.ofNullable(lastCallTimes.get(installationId));
    }

    public void remove(String installationId) {
        lastCallTimes.remove(installationId);
    }
}
