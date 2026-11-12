package com.sky.job.system.dto;

import java.util.List;

public record RecentFailuresResponse(
        List<RecentJobExecutionFailureResponse> jobExecutions,
        List<RecentNotificationFailureResponse> notifications
) {
}
