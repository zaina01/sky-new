package com.sky.job.plugin.storage;

import com.sky.job.plugin.security.PluginRuntimePrincipal;
import com.sky.job.plugin.storage.mapper.PluginTaskLogMapper;
import com.sky.plugin.protocol.PluginLogEvent;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PluginLogStore {
    private final PluginTaskLogMapper mapper;

    public PluginLogStore(PluginTaskLogMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(PluginRuntimePrincipal principal, PluginLogEvent event, String message) {
        mapper.insert(
                principal.installationId(),
                principal.pluginId(),
                principal.instanceId(),
                event.getJobId(),
                event.getRequestId(),
                event.getLevel().name().replace("LOG_LEVEL_", ""),
                message,
                event.getSequence(),
                Timestamp.from(Instant.now()));
    }

    public List<PluginTaskLog> listByRequest(String installationId, String requestId, Integer page, Integer size) {
        int normalizedPage = Math.max(page == null ? 1 : page, 1);
        int normalizedSize = Math.min(Math.max(size == null ? 20 : size, 1), 100);
        return mapper.listByRequest(installationId, requestId, normalizedSize, (normalizedPage - 1) * normalizedSize);
    }

    public long countByRequest(String installationId, String requestId) {
        return mapper.countByRequest(installationId, requestId);
    }

    public long countOlderThan(LocalDateTime cutoff) {
        return mapper.countOlderThan(cutoff);
    }

    public int deleteOlderThan(LocalDateTime cutoff, int limit) {
        return mapper.deleteOlderThan(cutoff, limit);
    }
}
