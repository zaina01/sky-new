package com.sky.job.notification;

import java.util.Map;

public record HostNotificationEvent(
        NotificationEventType eventType,
        String title,
        String content,
        String severity,
        Long executionId,
        String installationId,
        String pluginId,
        String instanceId,
        String jobId,
        String sourceOwnerUserId,
        Long sourceOwnerDepartmentId,
        Map<String, String> labels
) {
    public HostNotificationEvent {
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }

    public HostNotificationEvent(
            NotificationEventType eventType,
            String title,
            String content,
            String severity,
            Long executionId,
            String installationId,
            String pluginId,
            String instanceId,
            String jobId,
            Map<String, String> labels
    ) {
        this(eventType, title, content, severity, executionId, installationId, pluginId, instanceId,
                jobId, null, null, labels);
    }
}
