package com.sky.job.notification.provider;

import com.sky.job.notification.NotificationChannel;
import com.sky.job.notification.NotificationChannelType;
import com.sky.job.notification.NotificationSendCommand;
import com.sky.job.notification.NotificationSendResult;

import java.util.List;

public interface NotificationProvider {
    NotificationChannelType type();

    void validateConfig(String configJson);

    default List<NotificationDeliveryPart> plan(NotificationChannel channel, NotificationSendCommand command) {
        return List.of(NotificationDeliveryPart.single(command.title(), command.content()));
    }

    NotificationSendResult send(NotificationChannel channel, NotificationSendCommand command);
}
