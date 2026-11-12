package com.sky.job.system.dto;

import java.time.LocalDateTime;

public record OverviewWindowResponse(
        int windowHours,
        LocalDateTime startTime,
        LocalDateTime endTime,
        int trendDays,
        int recentLimit
) {
}
