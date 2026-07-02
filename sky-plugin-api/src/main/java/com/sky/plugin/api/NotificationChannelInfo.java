package com.sky.plugin.api;

public record NotificationChannelInfo(
        String channelId,
        String channelType,
        String displayName,
        boolean enabled
) {
}
