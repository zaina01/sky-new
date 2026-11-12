package com.sky.job.plugin.runtime.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;

import java.util.List;

public record ReplaceNotificationChannelsRequest(@NotNull List<@NotBlank String> channelIds) {
    public ReplaceNotificationChannelsRequest {
        channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
    }
}
