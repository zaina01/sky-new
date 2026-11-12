package com.sky.plugin.runner;

import com.sky.plugin.api.PluginServiceClient;
import com.sky.plugin.api.PluginServiceResponse;
import com.sky.plugin.protocol.PluginServiceCallRequest;
import com.sky.plugin.protocol.PluginServiceCallResponse;

class DefaultPluginServiceClient implements PluginServiceClient {
    private final String pluginId;
    private final String jobId;
    private final String requestId;
    private final HostGrpcClient hostClient;

    DefaultPluginServiceClient(String pluginId, String jobId, String requestId, HostGrpcClient hostClient) {
        this.pluginId = pluginId;
        this.jobId = jobId;
        this.requestId = requestId;
        this.hostClient = hostClient;
    }

    @Override
    public PluginServiceResponse call(String targetPluginId, String operation, String requestJson) {
        return call(targetPluginId, "", operation, requestJson);
    }

    @Override
    public PluginServiceResponse callInstallation(String targetInstallationId, String operation, String requestJson) {
        return call("", targetInstallationId, operation, requestJson);
    }

    private PluginServiceResponse call(String targetPluginId, String targetInstallationId, String operation, String requestJson) {
        PluginServiceCallResponse response = hostClient.callPluginService(PluginServiceCallRequest.newBuilder()
                .setCallerPluginId(pluginId)
                .setTargetPluginId(targetPluginId == null ? "" : targetPluginId)
                .setTargetInstallationId(targetInstallationId == null ? "" : targetInstallationId)
                .setOperation(operation == null ? "" : operation)
                .setRequestJson(requestJson == null ? "{}" : requestJson)
                .setJobId(jobId == null ? "" : jobId)
                .setRequestId(requestId == null ? "" : requestId)
                .build());
        return new PluginServiceResponse(
                response.getSuccess(),
                response.getResultJson(),
                response.getMessage(),
                response.getErrorType(),
                response.getErrorMessage()
        );
    }
}
