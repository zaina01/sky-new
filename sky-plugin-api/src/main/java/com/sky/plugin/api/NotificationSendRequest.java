package com.sky.plugin.api;

import java.util.List;
import java.util.Map;

public record NotificationSendRequest(
        List<String> channelIds,
        String title,
        String content,
        String severity,
        Map<String, String> labels
) {
    public NotificationSendRequest {
        channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
        labels = labels == null ? Map.of() : Map.copyOf(labels);
    }
}
