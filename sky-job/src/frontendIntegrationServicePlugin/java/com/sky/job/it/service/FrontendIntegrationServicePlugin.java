package com.sky.job.it.service;

import com.sky.plugin.api.PluginCapability;
import com.sky.plugin.api.PluginServiceRequest;
import com.sky.plugin.api.PluginServiceResponse;
import com.sky.plugin.api.ServicePlugin;
import com.sky.plugin.api.SkyPlugin;
import com.sky.plugin.api.TaskExecutionContext;

@SkyPlugin(
        pluginId = "frontend-integration-service",
        version = "1.0.0",
        displayName = "Frontend Integration Service Plugin",
        capabilities = PluginCapability.SERVICE
)
public class FrontendIntegrationServicePlugin implements ServicePlugin {
    @Override
    public PluginServiceResponse call(PluginServiceRequest request, TaskExecutionContext context) {
        if (!"echo".equals(request.operation())) {
            return PluginServiceResponse.failed(
                    "Unsupported frontend integration service operation",
                    IllegalArgumentException.class.getName(),
                    request.operation()
            );
        }
        System.out.printf(
                "frontend integration service: callerInstallationId=%s targetInstallationId=%s operation=%s request=%s%n",
                request.callerInstallationId(),
                request.targetInstallationId(),
                request.operation(),
                request.requestJson()
        );
        return PluginServiceResponse.success(
                "{\"echo\":true,\"operation\":\"echo\"}",
                "frontend integration service completed"
        );
    }
}
