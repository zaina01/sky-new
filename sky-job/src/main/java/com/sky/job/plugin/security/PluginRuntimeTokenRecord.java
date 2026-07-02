package com.sky.job.plugin.security;

import java.time.Instant;
import java.util.Set;

record PluginRuntimeTokenRecord(
        String pluginId,
        String instanceId,
        byte[] tokenDigest,
        Set<PluginCapability> capabilities,
        Instant issuedAt,
        Instant expiresAt,
        java.time.Duration ttl
) {
    PluginRuntimeTokenRecord renew(Instant now) {
        return new PluginRuntimeTokenRecord(pluginId, instanceId, tokenDigest, capabilities, issuedAt, now.plus(ttl), ttl);
    }

    PluginRuntimePrincipal principal() {
        return new PluginRuntimePrincipal(pluginId, instanceId, capabilities, issuedAt, expiresAt);
    }
}
