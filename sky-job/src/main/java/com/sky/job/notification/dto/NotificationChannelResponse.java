package com.sky.job.notification.dto;

import com.sky.job.notification.NotificationChannel;
import com.sky.job.notification.NotificationChannelType;
import com.sky.job.notification.NotificationSecretMasker;

import java.time.LocalDateTime;

public record NotificationChannelResponse(
        String channelId,
        NotificationChannelType channelType,
        String displayName,
        boolean enabled,
        String configMaskedJson,
        String remark,
        String ownerUserId,
        String ownerUsername,
        Long ownerDepartmentId,
        String ownerDepartmentName,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static NotificationChannelResponse from(NotificationChannel channel, NotificationSecretMasker masker) {
        return new NotificationChannelResponse(
                channel.channelId(),
                channel.channelType(),
                channel.displayName(),
                channel.enabled(),
                masker.maskConfig(channel.configJson()),
                channel.remark(),
                channel.ownerUserId(),
                channel.ownerUsername(),
                channel.ownerDepartmentId(),
                channel.ownerDepartmentName(),
                channel.createdBy(),
                channel.updatedBy(),
                channel.createdAt(),
                channel.updatedAt()
        );
    }
}
