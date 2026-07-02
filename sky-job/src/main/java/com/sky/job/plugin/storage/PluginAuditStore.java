package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.mapper.PluginAuditLogMapper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class PluginAuditStore {
    private final PluginAuditLogMapper mapper;

    public PluginAuditStore(PluginAuditLogMapper mapper) {
        this.mapper = mapper;
    }

    public void insert(String pluginId, String instanceId, String actor, String action, String detailJson) {
        mapper.insert(safe(pluginId), safe(instanceId), safe(actor), action, detailJson == null || detailJson.isBlank() ? "{}" : detailJson);
    }

    public List<PluginAuditLog> list(
            String pluginId,
            String instanceId,
            String actor,
            String action,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer page,
            Integer size
    ) {
        return mapper.list(
                trim(pluginId),
                trim(instanceId),
                trim(actor),
                like(actor),
                trim(action),
                startTime,
                endTime,
                normalizedSize(size),
                (normalizedPage(page) - 1) * normalizedSize(size));
    }

    public long count(
            String pluginId,
            String instanceId,
            String actor,
            String action,
            LocalDateTime startTime,
            LocalDateTime endTime
    ) {
        return mapper.count(trim(pluginId), trim(instanceId), trim(actor), like(actor), trim(action), startTime, endTime);
    }

    public long countOlderThan(LocalDateTime cutoff) {
        return mapper.countOlderThan(cutoff);
    }

    public int deleteOlderThan(LocalDateTime cutoff, int limit) {
        return mapper.deleteOlderThan(cutoff, limit);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private String trim(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String like(String value) {
        return value == null || value.isBlank() ? null : "%" + value.trim() + "%";
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
