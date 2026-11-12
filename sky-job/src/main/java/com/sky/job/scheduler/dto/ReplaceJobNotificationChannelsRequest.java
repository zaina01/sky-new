package com.sky.job.scheduler.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record ReplaceJobNotificationChannelsRequest(@NotNull List<@NotBlank String> channelIds) {
    public ReplaceJobNotificationChannelsRequest {
        channelIds = channelIds == null ? List.of() : List.copyOf(channelIds);
    }
}
