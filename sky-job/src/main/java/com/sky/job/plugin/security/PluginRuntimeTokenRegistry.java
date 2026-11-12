package com.sky.job.plugin.security;

import org.springframework.stereotype.Component;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class PluginRuntimeTokenRegistry {
    private final SecureRandom secureRandom = new SecureRandom();
    private final Map<String, PluginRuntimeTokenRecord> records = new ConcurrentHashMap<>();

    public String register(
            String installationId,
            String pluginId,
            String instanceId,
            Long jobId,
            Long executionId,
            Set<PluginCapability> capabilities,
            Duration ttl
    ) {
        byte[] tokenBytes = new byte[32];
        secureRandom.nextBytes(tokenBytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);
        Instant issuedAt = Instant.now();
        records.put(key(pluginId, instanceId), new PluginRuntimeTokenRecord(
                installationId,
                pluginId,
                instanceId,
                jobId,
                executionId,
                digest(token),
                Set.copyOf(capabilities),
                issuedAt,
                issuedAt.plus(ttl),
                ttl
        ));
        return token;
    }

    public Optional<PluginRuntimePrincipal> authenticate(String pluginId, String instanceId, String token) {
        PluginRuntimeTokenRecord record = records.get(key(pluginId, instanceId));
        if (record == null || record.expiresAt().isBefore(Instant.now())) {
            return Optional.empty();
        }
        if (!MessageDigest.isEqual(record.tokenDigest(), digest(token))) {
            return Optional.empty();
        }
        if (record.capabilities().contains(PluginCapability.SERVICE)) {
            Instant now = Instant.now();
            PluginRuntimeTokenRecord renewed = record.renew(now);
            records.put(key(pluginId, instanceId), renewed);
            return Optional.of(renewed.principal());
        }
        return Optional.of(record.principal());
    }

    public void revoke(String pluginId, String instanceId) {
        records.remove(key(pluginId, instanceId));
    }

    private byte[] digest(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private String key(String pluginId, String instanceId) {
        return pluginId + ":" + instanceId;
    }
}
