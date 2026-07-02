package com.sky.job.scheduler.dto;

public record CronValidationResponse(
        boolean valid,
        String message
) {
    public static CronValidationResponse ok() {
        return new CronValidationResponse(true, "");
    }

    public static CronValidationResponse error(String message) {
        return new CronValidationResponse(false, message);
    }
}
