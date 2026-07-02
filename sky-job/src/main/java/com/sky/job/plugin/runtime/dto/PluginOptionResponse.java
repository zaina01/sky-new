package com.sky.job.plugin.runtime.dto;

import com.sky.job.plugin.runtime.PluginRuntimeState;

public record PluginOptionResponse(
        String pluginId,
        String pluginName,
        String version,
        PluginRuntimeState state
) {
}
