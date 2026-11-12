package com.sky.job.plugin.security;

import java.time.Instant;
import java.util.Set;

public record PluginRuntimePrincipal(
        String installationId,
        String pluginId,
        String instanceId,
        Long jobId,
        Long executionId,
        Set<PluginCapability> capabilities,
        Instant issuedAt,
        Instant expiresAt
) {
    public boolean hasCapability(PluginCapability capability) {
        return capabilities.contains(capability);
    }

    public boolean taskBound() {
        return jobId != null;
    }
}
