package com.sky.job.scheduler;

import java.time.LocalDateTime;

public record ScheduledJob(
        long id,
        String jobName,
        String pluginId,
        String cronExpression,
        JobStatus jobStatus,
        MisfirePolicy misfirePolicy,
        boolean concurrent,
        String inputJson,
        long timeoutMillis,
        ExecutedStatus executedStatus,
        String remark,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
