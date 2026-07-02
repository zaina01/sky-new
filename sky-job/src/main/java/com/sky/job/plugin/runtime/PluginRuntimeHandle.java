package com.sky.job.plugin.runtime;

import java.nio.file.Path;

public interface PluginRuntimeHandle {
    String pluginId();

    String instanceId();

    int port();

    String token();

    Path logPath();
}
