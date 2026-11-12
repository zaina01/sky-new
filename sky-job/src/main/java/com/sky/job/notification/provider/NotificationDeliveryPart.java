package com.sky.job.notification.provider;

public record NotificationDeliveryPart(
        String title,
        String content,
        int partIndex,
        int partCount
) {
    public NotificationDeliveryPart {
        if (partIndex < 1 || partCount < 1 || partIndex > partCount) {
            throw new IllegalArgumentException("Invalid notification delivery part");
        }
    }

    public static NotificationDeliveryPart single(String title, String content) {
        return new NotificationDeliveryPart(title, content, 1, 1);
    }

    public boolean multipart() {
        return partCount > 1;
    }
}
