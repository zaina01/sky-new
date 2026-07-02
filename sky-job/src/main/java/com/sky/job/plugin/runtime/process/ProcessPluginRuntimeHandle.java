package com.sky.job.plugin.runtime.process;

import com.sky.job.plugin.runtime.PluginRuntimeHandle;

import java.nio.file.Path;

public record ProcessPluginRuntimeHandle(
        String pluginId,
        String instanceId,
        int port,
        String token,
        Path logPath,
        Process process
) implements PluginRuntimeHandle {
}
