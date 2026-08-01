package com.sky.job.common.mapper;

import java.time.LocalDate;

public record DailyStatusCountRow(
        LocalDate bucketDate,
        String status,
        long total
) {
}
