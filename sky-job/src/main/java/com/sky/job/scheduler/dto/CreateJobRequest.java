package com.sky.job.scheduler.dto;

import com.sky.job.scheduler.MisfirePolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateJobRequest(
        @NotBlank String jobName,
        @NotBlank String pluginId,
        @NotBlank String cronExpression,
        @NotNull MisfirePolicy misfirePolicy,
        boolean concurrent,
        String inputJson,
        @Min(1) long timeoutMillis,
        String remark
) {
}
