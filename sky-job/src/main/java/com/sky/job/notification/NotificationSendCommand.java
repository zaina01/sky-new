package com.sky.job.notification;

import java.util.List;
import java.util.Map;

public record NotificationSendCommand(
        NotificationSourceType sourceType,
        String requestId,
        String pluginId,
        String instanceId,
        String jobId,
        Long executionId,
        List<String> channelIds,
        String title,
        String content,
        String severity,
        Map<String, String> labels
) {
    public NotificationSendCommand {
        channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }
}
