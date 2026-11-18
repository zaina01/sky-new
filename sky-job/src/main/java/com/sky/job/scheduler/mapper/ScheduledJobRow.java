package com.sky.job.scheduler.mapper;

import java.time.LocalDateTime;

public record ScheduledJobRow(
        long id,
        String jobName,
        String pluginInstallationId,
        String pluginId,
        String pluginDisplayName,
        String cronExpression,
        String jobStatus,
        String misfirePolicy,
        boolean concurrent,
        String inputJson,
        long timeoutMillis,
        String executedStatus,
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
    public ScheduledJobRow(
        long id,
        String jobName,
        String pluginInstallationId,
        String pluginId,
        String pluginDisplayName,
        String cronExpression,
        String jobStatus,
        String misfirePolicy,
        boolean concurrent,
        String inputJson,
        long timeoutMillis,
        String executedStatus,
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
