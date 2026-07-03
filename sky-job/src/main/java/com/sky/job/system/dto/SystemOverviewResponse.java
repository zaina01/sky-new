package com.sky.job.system.dto;

import java.util.List;

public record SystemOverviewResponse(
        long uploadedPlugins,
        long enabledJobs,
        long failedExecutions,
        long pendingRetryNotifications,
        OverviewWindowResponse window,
        List<StatusCountResponse> pluginRuntimeStates,
        List<StatusCountResponse> jobStatuses,
        List<StatusCountResponse> jobExecutedStatuses,
        List<StatusCountResponse> executionStatusesInWindow,
        List<StatusCountResponse> notificationStatusesInWindow,
        TrendOverviewResponse trends,
        RecentFailuresResponse recentFailures
) {
}
