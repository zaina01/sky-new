package com.sky.job.notification;

public record NotificationSendReceipt(
        String channelId,
        NotificationSendStatus status,
        String message,
        long historyId
) {
}
