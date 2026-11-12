package com.sky.job.plugin.callback.dto;

import com.sky.job.plugin.callback.PluginServicePermission;

import java.time.LocalDateTime;

public record PluginServicePermissionResponse(
        long id,
        String callerInstallationId,
        String callerPluginId,
        String targetInstallationId,
        String targetPluginId,
        String operation,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static PluginServicePermissionResponse from(PluginServicePermission permission) {
        return new PluginServicePermissionResponse(
                permission.id(),
                permission.callerInstallationId(),
                permission.callerPluginId(),
                permission.targetInstallationId(),
                permission.targetPluginId(),
                permission.operation(),
                permission.enabled(),
                permission.createdAt(),
                permission.updatedAt()
        );
    }
}
