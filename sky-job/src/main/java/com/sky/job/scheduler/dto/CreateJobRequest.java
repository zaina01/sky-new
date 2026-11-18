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

    @com.fasterxml.jackson.annotation.JsonAnySetter
    public void unknown(String name, com.fasterxml.jackson.databind.JsonNode value) {
        if ("scriptOptions".equals(name) && value != null && !value.isNull() && !(value.isObject() && value.isEmpty()))
            throw new IllegalArgumentException("Job scriptOptions is no longer supported; configure environment and arguments on the installation");
    }
    public CreateJobRequest {
        notificationChannelIds = notificationChannelIds == null ? List.of() : List.copyOf(notificationChannelIds);
    }
}
