package com.open.demo;

import com.sky.plugin.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@SkyPlugin(
        pluginId = "demo",
        version = "1.0.1",
        displayName = "Demo Plugin",
        capabilities = {PluginCapability.TASK, PluginCapability.CONFIG, PluginCapability.ACCOUNT,PluginCapability.NOTIFICATION},
        configClass = DemoConfig.class,
        accountClass = DemoAccount.class
)
public class TaskServiceImpl implements TaskPlugin {
    public static final String LOGIN = "login";
    private static final Logger log = LoggerFactory.getLogger(TaskServiceImpl.class);

    @Override
    public TaskResult run(TaskExecutionContext context) {
        log.info("demo plugin started");
        DemoConfig config = context.config().get(DemoConfig.class);
        log.info("config: {}", config);
        List<DemoAccount> list = context.accounts().list(DemoAccount.class);
        log.info("enabled account count: {}", list.size());
        log.info("demo task business log written to runner log");
        log.info("demo plugin finished");
        PluginNotificationClient notifications = context.notifications();
//        List<NotificationChannelInfo> notificationChannelInfos = notifications.listChannels();
//        notifications.send(notificationChannelInfos.getFirst().channelId(),"test","测试");

        PluginServiceResponse call = context.services().call("chinaTelecom", LOGIN, list.getFirst().getAccountId());
        log.info("demo plugin call response: {}", call);
        return TaskResult.success("demo completed", "{\"ok\":true}");
    }
}
