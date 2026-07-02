package com.sky.job.scheduler.dto;

import com.sky.job.scheduler.ExecutedStatus;
import com.sky.job.scheduler.JobStatus;
import com.sky.job.scheduler.MisfirePolicy;
import com.sky.job.scheduler.ScheduledJob;

import java.time.LocalDateTime;

public record JobResponse(
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
    public static JobResponse from(ScheduledJob job) {
        return new JobResponse(
                job.id(),
                job.jobName(),
                job.pluginId(),
                job.cronExpression(),
                job.jobStatus(),
                job.misfirePolicy(),
                job.concurrent(),
                job.inputJson(),
                job.timeoutMillis(),
                job.executedStatus(),
                job.remark(),
                job.createdBy(),
                job.updatedBy(),
                job.createdAt(),
                job.updatedAt()
        );
    }
}
