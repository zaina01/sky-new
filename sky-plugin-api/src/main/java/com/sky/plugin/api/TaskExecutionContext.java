package com.sky.plugin.api;

public interface TaskExecutionContext {
    String pluginId();

    String jobId();

    String requestId();

    String inputJson();

    PluginConfigClient config();

    PluginAccountClient accounts();

    PluginNotificationClient notifications();

    PluginServiceClient services();

    PluginDataClient data();
}
