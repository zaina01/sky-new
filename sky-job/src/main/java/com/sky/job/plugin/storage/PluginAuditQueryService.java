package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.dto.PluginAuditLogResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class PluginAuditQueryService {
    private final PluginAuditStore auditStore;

    public PluginAuditQueryService(PluginAuditStore auditStore) {
        this.auditStore = auditStore;
    }

    public PageResponse<PluginAuditLogResponse> listAuditLogs(
            String pluginId,
            String instanceId,
            String actor,
            String action,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer page,
            Integer size
    ) {
        validateTimeRange(startTime, endTime);
        return new PageResponse<>(
                auditStore.list(pluginId, instanceId, actor, action, startTime, endTime, page, size).stream().map(PluginAuditLogResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                auditStore.count(pluginId, instanceId, actor, action, startTime, endTime)
        );
    }

    private void validateTimeRange(LocalDateTime startTime, LocalDateTime endTime) {
        if (startTime != null && endTime != null && startTime.isAfter(endTime)) {
            throw new IllegalArgumentException("Invalid time range");
        }
    }

    private int normalizedPage(Integer page) {
        return Math.max(page == null ? 1 : page, 1);
    }

    private int normalizedSize(Integer size) {
        return Math.min(Math.max(size == null ? 20 : size, 1), 100);
    }
}
