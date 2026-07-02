package com.sky.job.plugin.security;

import java.time.Instant;
import java.util.Set;

public record PluginRuntimePrincipal(
        String pluginId,
        String instanceId,
        Set<PluginCapability> capabilities,
        Instant issuedAt,
        Instant expiresAt
) {
    public boolean hasCapability(PluginCapability capability) {
        return capabilities.contains(capability);
    }
}
