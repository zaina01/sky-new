package com.sky.plugin.api;

public interface PluginServiceClient {
    PluginServiceResponse call(String targetPluginId, String operation, String requestJson);
}
