package com.sky.plugin.api;

import java.util.List;

public interface PluginNotificationClient {
    List<NotificationChannelInfo> listChannels();

    NotificationSendResult send(String channelId, String title, String content);

    NotificationSendResult send(NotificationSendRequest request);

    NotificationSendResult sendAll(String title, String content);

    NotificationSendResult sendAll(NotificationSendRequest request);
}
