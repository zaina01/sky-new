package com.sky.job.notification;

public record NotificationSendResult(
        boolean success,
        String providerStatusCode,
        String providerMessage,
        String errorType,
        String errorMessage
) {
    public static NotificationSendResult success(String providerStatusCode, String providerMessage) {
        return new NotificationSendResult(true, providerStatusCode, providerMessage, null, null);
    }

    public static NotificationSendResult failed(String errorType, String errorMessage, String providerStatusCode, String providerMessage) {
        return new NotificationSendResult(false, providerStatusCode, providerMessage, errorType, errorMessage);
    }
}
