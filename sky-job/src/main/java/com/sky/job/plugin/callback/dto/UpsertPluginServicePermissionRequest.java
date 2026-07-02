package com.sky.job.plugin.callback.dto;

public record UpsertPluginServicePermissionRequest(
        String callerPluginId,
        String targetPluginId,
        String operation,
        Boolean enabled
) {
}
