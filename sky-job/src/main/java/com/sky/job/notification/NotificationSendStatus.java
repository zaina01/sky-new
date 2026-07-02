package com.sky.job.notification;

public enum NotificationSendStatus {
    PENDING,
    SENDING,
    RETRY_WAITING,
    SUCCESS,
    FAILED,
    SKIPPED
}
