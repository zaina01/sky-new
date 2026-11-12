package com.sky.job.plugin.runtime.dto;

public record UpdatePluginRuntimeDebugRequest(
        Boolean enabled,
        Integer port,
        Boolean suspend
) {
}
