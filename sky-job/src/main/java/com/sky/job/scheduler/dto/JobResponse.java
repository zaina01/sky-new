package com.sky.job.scheduler.dto;

import com.sky.job.scheduler.ExecutedStatus;
import com.sky.job.scheduler.JobStatus;
import com.sky.job.scheduler.MisfirePolicy;
import com.sky.job.scheduler.ScheduledJob;

import java.time.LocalDateTime;
import java.util.List;

public record JobResponse(
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
        List<String> notificationChannelIds,
        String ownerUserId,
        String ownerUsername,
        Long ownerDepartmentId,
        String ownerDepartmentName,
        String createdBy,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static JobResponse from(ScheduledJob job) {
        return from(job, List.of());
    }

    public static JobResponse from(ScheduledJob job, List<String> notificationChannelIds) {
        return new JobResponse(
                job.id(),
                job.jobName(),
                job.pluginInstallationId(),
                job.pluginId(),
                job.pluginDisplayName(),
                job.cronExpression(),
                job.jobStatus(),
                job.misfirePolicy(),
                job.concurrent(),
                job.inputJson(),
                job.timeoutMillis(),
                job.executedStatus(),
                job.remark(),
                notificationChannelIds == null ? List.of() : List.copyOf(notificationChannelIds),
                job.ownerUserId(),
                job.ownerUsername(),
                job.ownerDepartmentId(),
                job.ownerDepartmentName(),
                job.createdBy(),
                job.updatedBy(),
                job.createdAt(),
                job.updatedAt()
        );
    }
}
