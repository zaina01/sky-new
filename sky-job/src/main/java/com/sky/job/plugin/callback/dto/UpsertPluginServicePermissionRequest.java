package com.sky.job.plugin.callback.dto;

public record UpsertPluginServicePermissionRequest(
        String callerInstallationId,
        String targetInstallationId,
        String operation,
        Boolean enabled
) {
}
