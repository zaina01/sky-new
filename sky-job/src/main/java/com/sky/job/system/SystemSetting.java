package com.sky.job.system;

import java.time.LocalDateTime;

public record SystemSetting(
        String settingKey,
        String category,
        String valueText,
        String valueType,
        String description,
        boolean editable,
        String updatedBy,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
