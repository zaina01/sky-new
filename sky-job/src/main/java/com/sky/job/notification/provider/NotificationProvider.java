package com.sky.job.notification.provider;

import com.sky.job.notification.NotificationChannel;
import com.sky.job.notification.NotificationChannelType;
import com.sky.job.notification.NotificationSendCommand;
import com.sky.job.notification.NotificationSendResult;

public interface NotificationProvider {
    NotificationChannelType type();

    void validateConfig(String configJson);

    NotificationSendResult send(NotificationChannel channel, NotificationSendCommand command);
}
