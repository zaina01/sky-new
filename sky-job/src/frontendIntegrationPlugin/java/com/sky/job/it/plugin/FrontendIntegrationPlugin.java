package com.sky.job.it.plugin;

import com.sky.plugin.api.NotificationChannelInfo;
import com.sky.plugin.api.PluginCapability;
import com.sky.plugin.api.PluginServiceResponse;
import com.sky.plugin.api.SkyPlugin;
import com.sky.plugin.api.TaskExecutionContext;
import com.sky.plugin.api.TaskPlugin;
import com.sky.plugin.api.TaskResult;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@SkyPlugin(
        pluginId = "frontend-integration",
        version = "1.0.0",
        displayName = "Frontend Integration Plugin",
        capabilities = {
                PluginCapability.TASK,
                PluginCapability.CONFIG,
                PluginCapability.ACCOUNT,
                PluginCapability.NOTIFICATION
        },
        configClass = FrontendIntegrationConfig.class,
        accountClass = FrontendIntegrationAccount.class
)
public class FrontendIntegrationPlugin implements TaskPlugin {
    private static final Pattern CHANNEL_ID = Pattern.compile("\\\"channelId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
    private static final Pattern SERVICE_INSTALLATION_ID = Pattern.compile("\\\"serviceInstallationId\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");

    @Override
    public TaskResult run(TaskExecutionContext context) {
        FrontendIntegrationConfig config = context.config().get(FrontendIntegrationConfig.class);
        List<FrontendIntegrationAccount> accounts = context.accounts().list(FrontendIntegrationAccount.class);
        List<NotificationChannelInfo> channels = context.notifications().listChannels();
        String input = context.inputJson() == null ? "" : context.inputJson();
        String requestedChannelId = requestedChannelId(input);
        if (requestedChannelId.isBlank() && input.replace(" ", "").contains("\"push\":true") && !channels.isEmpty()) {
            requestedChannelId = channels.getFirst().channelId();
        }
        if (!requestedChannelId.isBlank()) {
            context.notifications().send(requestedChannelId, "frontend-integration", "installation=" + context.pluginId());
        }
        String serviceInstallationId = value(input, SERVICE_INSTALLATION_ID);
        if (!serviceInstallationId.isBlank()) {
            PluginServiceResponse response = context.services().callInstallation(
                    serviceInstallationId,
                    "echo",
                    "{\"source\":\"frontend-integration-task\"}"
            );
            if (!response.success()) {
                return TaskResult.failure("frontend integration service call failed: " + response.errorMessage());
            }
        }
        System.out.printf(
                "frontend integration task: jobId=%s channels=%s accounts=%d message=%s%n",
                context.jobId(),
                channels.stream().map(NotificationChannelInfo::channelId).toList(),
                accounts.size(),
                config == null ? "" : config.getMessage()
        );
        return TaskResult.success("frontend integration completed", "{\"channelCount\":" + channels.size() + "}");
    }

    private String requestedChannelId(String input) {
        return value(input, CHANNEL_ID);
    }

    private String value(String input, Pattern pattern) {
        Matcher matcher = pattern.matcher(input);
        return matcher.find() ? matcher.group(1) : "";
    }
}
