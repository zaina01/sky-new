package com.sky.job.plugin.runtime;

import java.time.Duration;

public interface PluginRuntimeAdapter {
    PluginRuntimeHandle start(PluginRuntimeSpec spec);

    void stop(PluginRuntimeHandle handle, Duration timeout);

    PluginRuntimeState inspect(PluginRuntimeHandle handle);
}
