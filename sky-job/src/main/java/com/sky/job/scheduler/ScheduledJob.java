package com.sky.job.scheduler;

import java.time.LocalDateTime;

public record ScheduledJob(
        long id,
        String jobName,
        String pluginInstallationId,
        String pluginId,
        String pluginDisplayName,
        String cronExpression,
        JobStatus jobStatus,
        MisfirePolicy misfirePolicy,
        boolean concurrent,
        String inputJson,
        long timeoutMillis,
        ExecutedStatus executedStatus,
        String remark,
        String ownerUserId,
        String ownerUsername,
        Long ownerDepartmentId,
        String ownerDepartmentName,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        String executionMode
) {
    public ScheduledJob(
        long id,
        String jobName,
        String pluginInstallationId,
        String pluginId,
        String pluginDisplayName,
        String cronExpression,
        JobStatus jobStatus,
        MisfirePolicy misfirePolicy,
        boolean concurrent,
        String inputJson,
        long timeoutMillis,
        ExecutedStatus executedStatus,
        String remark,
        String ownerUserId,
        String ownerUsername,
        Long ownerDepartmentId,
        String ownerDepartmentName,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
    ) {
        this(id, jobName, pluginInstallationId, pluginId, pluginDisplayName, cronExpression, jobStatus, misfirePolicy, concurrent, inputJson, timeoutMillis, executedStatus, remark, ownerUserId, ownerUsername, ownerDepartmentId, ownerDepartmentName, createdBy, updatedBy, createdAt, updatedAt, "plugin");
    }

}
