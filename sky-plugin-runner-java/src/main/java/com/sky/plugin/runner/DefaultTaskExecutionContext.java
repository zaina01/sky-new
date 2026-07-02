package com.sky.plugin.runner;

import com.sky.plugin.api.PluginAccountClient;
import com.sky.plugin.api.PluginConfigClient;
import com.sky.plugin.api.PluginDataClient;
import com.sky.plugin.api.PluginNotificationClient;
import com.sky.plugin.api.PluginServiceClient;
import com.sky.plugin.api.TaskExecutionContext;
import com.sky.plugin.protocol.TaskRunRequest;

class DefaultTaskExecutionContext implements TaskExecutionContext {
    private final TaskRunRequest request;
    private final PluginConfigClient config;
    private final PluginAccountClient accounts;
    private final PluginNotificationClient notifications;
    private final PluginServiceClient services;
    private final PluginDataClient data;

    DefaultTaskExecutionContext(TaskRunRequest request, RunnerEnvironment environment, HostGrpcClient hostClient) {
        this.request = request;
        this.config = new DefaultPluginConfigClient(request.getPluginId(), hostClient);
        this.accounts = new DefaultPluginAccountClient(request.getPluginId(), hostClient);
        this.notifications = new DefaultPluginNotificationClient(request, hostClient);
        this.services = new DefaultPluginServiceClient(request.getPluginId(), request.getJobId(), request.getRequestId(), hostClient);
        this.data = new DefaultPluginDataClient(request.getPluginId(), hostClient);
    }

    @Override
    public String pluginId() {
        return request.getPluginId();
    }

    @Override
    public String jobId() {
        return request.getJobId();
    }

    @Override
    public String requestId() {
        return request.getRequestId();
    }

    @Override
    public String inputJson() {
        return request.getInputJson();
    }

    @Override
    public PluginConfigClient config() {
        return config;
    }

    @Override
    public PluginAccountClient accounts() {
        return accounts;
    }

    @Override
    public PluginNotificationClient notifications() {
        return notifications;
    }

    @Override
    public PluginServiceClient services() {
        return services;
    }

    @Override
    public PluginDataClient data() {
        return data;
    }
}
