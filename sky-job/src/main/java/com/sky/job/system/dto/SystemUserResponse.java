package com.sky.job.system.dto;

import com.sky.job.user.User;

import java.time.LocalDateTime;
import java.util.Set;

public record SystemUserResponse(
        String userId,
        String username,
        String email,
        String phone,
        String avatar,
        Long departmentId,
        String departmentName,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        Set<String> roles,
        Set<String> permissions
) {
    public static SystemUserResponse from(User user, Set<String> roles, Set<String> permissions) {
        return new SystemUserResponse(
                user.userId(),
                user.username(),
                user.email(),
                user.phone(),
                user.avatar(),
                user.departmentId(),
                user.departmentName(),
                user.enabled(),
                user.createdAt(),
                user.updatedAt(),
                roles,
                permissions
        );
    }
}
