package com.sky.job.plugin.storage.dto;

import com.sky.job.plugin.storage.PluginAuditLog;

import java.time.LocalDateTime;

public record PluginAuditLogResponse(
        Long id,
        String pluginId,
        String instanceId,
        String actor,
        String action,
        String detailJson,
        LocalDateTime createdAt
) {
    public static PluginAuditLogResponse from(PluginAuditLog log) {
        return new PluginAuditLogResponse(
                log.id(),
                log.pluginId(),
                log.instanceId(),
                log.actor(),
                log.action(),
                log.detailJson(),
                log.createdAt()
        );
    }
}
