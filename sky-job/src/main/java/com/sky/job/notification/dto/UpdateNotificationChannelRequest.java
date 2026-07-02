package com.sky.job.notification.dto;

import com.sky.job.notification.NotificationChannelType;

public record UpdateNotificationChannelRequest(
        NotificationChannelType channelType,
        String displayName,
        Boolean enabled,
        String configJson,
        String remark
) {
}
