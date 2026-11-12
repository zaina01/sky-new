package com.sky.job.plugin.security;

import java.time.Instant;
import java.util.Set;

record PluginRuntimeTokenRecord(
        String installationId,
        String pluginId,
        String instanceId,
        Long jobId,
        Long executionId,
        byte[] tokenDigest,
        Set<PluginCapability> capabilities,
        Instant issuedAt,
        Instant expiresAt,
        java.time.Duration ttl
) {
    PluginRuntimeTokenRecord renew(Instant now) {
        return new PluginRuntimeTokenRecord(installationId, pluginId, instanceId, jobId, executionId, tokenDigest, capabilities, issuedAt, now.plus(ttl), ttl);
    }

    PluginRuntimePrincipal principal() {
        return new PluginRuntimePrincipal(installationId, pluginId, instanceId, jobId, executionId, capabilities, issuedAt, expiresAt);
    }
}
