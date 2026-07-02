package com.sky.job.user;

import java.time.LocalDateTime;

public record User(
        String userId,
        String username,
        String passwordHash,
        String email,
        String phone,
        String avatar,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
}
