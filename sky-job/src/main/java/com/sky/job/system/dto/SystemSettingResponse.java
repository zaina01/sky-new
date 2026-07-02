package com.sky.job.system.dto;

import java.time.LocalDateTime;

public record SystemSettingResponse(
        String key,
        String category,
        String valueType,
        Object value,
        String valueText,
        String displayName,
        String description,
        String unit,
        String group,
        String placeholder,
        Object min,
        Object max,
        boolean editable,
        LocalDateTime updatedAt
) {
}
