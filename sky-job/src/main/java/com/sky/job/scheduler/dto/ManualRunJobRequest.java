package com.sky.job.scheduler.dto;

import jakarta.validation.constraints.Min;

public record ManualRunJobRequest(
        String inputJson,
        @Min(1) Long timeoutMillis
) {
}
