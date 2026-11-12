package com.sky.job.system.dto;

import java.util.List;

public record TrendOverviewResponse(
        List<DailyStatusCountResponse> jobExecutions,
        List<DailyStatusCountResponse> notifications
) {
}
