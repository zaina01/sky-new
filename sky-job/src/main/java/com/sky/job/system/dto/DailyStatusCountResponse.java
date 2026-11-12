package com.sky.job.system.dto;

import java.time.LocalDate;

public record DailyStatusCountResponse(
        LocalDate date,
        String status,
        long count
) {
}
