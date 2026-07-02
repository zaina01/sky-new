package com.sky.job.system;

import java.time.LocalDateTime;

public record Role(
        Long roleId,
        String roleName,
        String description,
        LocalDateTime createdAt
) {
}
