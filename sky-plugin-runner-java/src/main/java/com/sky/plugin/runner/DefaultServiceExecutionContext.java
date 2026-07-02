package com.sky.plugin.runner;

import com.sky.plugin.api.PluginAccountClient;
import com.sky.plugin.api.PluginConfigClient;
import com.sky.plugin.api.PluginDataClient;
import com.sky.plugin.api.PluginNotificationClient;
import com.sky.plugin.api.PluginServiceClient;
import com.sky.plugin.api.TaskExecutionContext;
import com.sky.plugin.protocol.PluginServiceRunRequest;

class DefaultServiceExecutionContext implements TaskExecutionContext {
    private final PluginServiceRunRequest request;
    private final PluginConfigClient config;
    private final PluginAccountClient accounts;
    private final PluginNotificationClient notifications;
    private final PluginServiceClient services;
    private final PluginDataClient data;

    DefaultServiceExecutionContext(PluginServiceRunRequest request, HostGrpcClient hostClient) {
        this.request = request;
        this.config = new DefaultPluginConfigClient(request.getTargetPluginId(), hostClient);
        this.accounts = new DefaultPluginAccountClient(request.getTargetPluginId(), hostClient);
        this.notifications = new DefaultPluginNotificationClient(request.getTargetPluginId(), "", request.getRequestId(), hostClient);
        this.services = new DefaultPluginServiceClient(request.getTargetPluginId(), "", request.getRequestId(), hostClient);
        this.data = new DefaultPluginDataClient(request.getTargetPluginId(), hostClient);
    }

    @Override
    public String pluginId() {
        return request.getTargetPluginId();
    }

    @Override
    public String jobId() {
        return "";
    }

    @Override
    public String requestId() {
        return request.getRequestId();
    }

    @Override
    public String inputJson() {
        return request.getRequestJson();
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
