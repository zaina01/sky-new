package com.sky.job.scheduler.dto;

import com.sky.job.scheduler.MisfirePolicy;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public record CreateJobRequest(
        @NotBlank String jobName,
        @NotBlank String pluginInstallationId,
        @NotBlank String cronExpression,
        @NotNull MisfirePolicy misfirePolicy,
        boolean concurrent,
        String inputJson,
        @Min(1) long timeoutMillis,
        String remark,
        List<@NotBlank String> notificationChannelIds
) {
    public CreateJobRequest {
        notificationChannelIds = notificationChannelIds == null ? List.of() : List.copyOf(notificationChannelIds);
    }
}
