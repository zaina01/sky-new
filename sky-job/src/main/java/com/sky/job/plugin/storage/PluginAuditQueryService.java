package com.sky.job.plugin.storage;

import com.sky.job.plugin.storage.dto.PluginAuditLogResponse;
import com.sky.job.scheduler.dto.PageResponse;
import org.springframework.stereotype.Service;
import com.sky.job.security.AuthenticatedUser;
import com.sky.job.security.DataScopeService;
import com.sky.job.security.PermissionNames;

import java.time.LocalDateTime;

@Service
public class PluginAuditQueryService {
    private final PluginAuditStore auditStore;
    private final DataScopeService dataScopeService;

    public PluginAuditQueryService(PluginAuditStore auditStore, DataScopeService dataScopeService) {
        this.auditStore = auditStore;
        this.dataScopeService = dataScopeService;
    }

    public PageResponse<PluginAuditLogResponse> listAuditLogs(
            String installationId,
            String pluginId,
            String instanceId,
            String actor,
            String action,
            LocalDateTime startTime,
            LocalDateTime endTime,
            Integer page,
            Integer size,
            AuthenticatedUser user
    ) {
        validateTimeRange(startTime, endTime);
        return new PageResponse<>(
                auditStore.list(installationId, pluginId, instanceId, actor, action, startTime, endTime, page, dataScopeService.resolve(user, PermissionNames.PLUGIN_AUDIT_READ), size).stream().map(PluginAuditLogResponse::from).toList(),
                normalizedPage(page),
                normalizedSize(size),
                auditStore.count(installationId, pluginId, instanceId, actor, action, startTime, endTime, dataScopeService.resolve(user, PermissionNames.PLUGIN_AUDIT_READ))
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
