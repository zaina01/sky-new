package com.sky.job.system.dto;

public record SystemOverviewResponse(
        long uploadedPlugins,
        long enabledJobs,
        long failedExecutions,
        long pendingRetryNotifications
) {
}
