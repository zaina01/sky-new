package com.sky.job.system;

import java.time.LocalDateTime;

public record Department(
        Long departmentId,
        Long parentId,
        String departmentName,
        int sortOrder,
        boolean enabled,
        boolean deleted,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
