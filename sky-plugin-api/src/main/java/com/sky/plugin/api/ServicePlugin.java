package com.sky.plugin.api;

public interface ServicePlugin {
    PluginServiceResponse call(PluginServiceRequest request, TaskExecutionContext context) throws Exception;
}
