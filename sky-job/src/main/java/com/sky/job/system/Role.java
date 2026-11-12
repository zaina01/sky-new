package com.sky.job.system;

import com.sky.job.security.DataScopeType;
import java.time.LocalDateTime;

public record Role(
        Long roleId,
        String roleName,
        String description,
        DataScopeType dataScope,
        LocalDateTime createdAt
) {
}
