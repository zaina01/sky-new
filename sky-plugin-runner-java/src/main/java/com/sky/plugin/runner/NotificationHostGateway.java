package com.sky.plugin.runner;

import com.sky.plugin.protocol.NotificationChannelListRequest;
import com.sky.plugin.protocol.NotificationChannelListResponse;
import com.sky.plugin.protocol.NotificationRequest;
import com.sky.plugin.protocol.NotificationResponse;

interface NotificationHostGateway {
    NotificationChannelListResponse listNotificationChannels(NotificationChannelListRequest request);

    NotificationResponse sendNotification(NotificationRequest request);
}
