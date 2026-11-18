package com.sky.job.plugin.runtime.dto;

import com.sky.job.plugin.runtime.PluginRuntimeState;

public record PluginOptionResponse(
        String installationId,
        String pluginId,
        String installationName,
        String pluginName,
        String version,
        String ownerUserId,
        Long ownerDepartmentId,
        PluginRuntimeState state,
        String executionMode
) {
    public PluginOptionResponse(
        String installationId,
        String pluginId,
        String installationName,
        String pluginName,
        String version,
        String ownerUserId,
        Long ownerDepartmentId,
        PluginRuntimeState state
    ) {
        this(installationId, pluginId, installationName, pluginName, version, ownerUserId, ownerDepartmentId, state, "plugin");
    }

}
